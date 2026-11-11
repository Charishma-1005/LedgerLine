package com.ledgerline.ledger.web;

import com.ledgerline.account.application.FinancialAccountService;
import com.ledgerline.account.domain.FinancialAccount;
import com.ledgerline.ledger.application.FinancialOperationService;
import com.ledgerline.ledger.application.FinancialOperationException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/transactions")
public class FinancialOperationController {
    private final FinancialOperationService operations;
    private final FinancialAccountService accounts;

    public FinancialOperationController(FinancialOperationService operations, FinancialAccountService accounts) {
        this.operations = operations;
        this.accounts = accounts;
    }

    @GetMapping
    public String history(@RequestParam(name = "type", defaultValue = "ALL") String requestedType,
                          Authentication authentication, Model model) {
        String selectedType = selectedFilter(requestedType);
        model.addAttribute("transactions", operations.history().stream()
                .filter(transaction -> selectedType.equals("ALL") || transaction.type().name().equals(selectedType))
                .toList());
        model.addAttribute("selectedType", selectedType);
        model.addAttribute("currentEmail", authentication.getName());
        return "transactions/history";
    }

    @GetMapping("/{transactionId}")
    public String details(@PathVariable long transactionId, Authentication authentication, Model model) {
        var transaction = operations.history().stream().filter(item -> item.id() == transactionId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("transaction", transaction);
        model.addAttribute("currentEmail", authentication.getName());
        return "transactions/detail";
    }

    @GetMapping("/new")
    public String newOperation(@RequestParam(name = "operation", defaultValue = "DEPOSIT") String requestedOperation,
                               Model model) {
        FinancialOperationForm form = new FinancialOperationForm();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        populateForm(model, form);
        model.addAttribute("selectedAction", selectedAction(requestedOperation));
        model.addAttribute("formAction", "/transactions/" + selectedAction(requestedOperation).toLowerCase(java.util.Locale.ROOT));
        model.addAttribute("currentEmail", org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName());
        return "transactions/new";
    }

    @PostMapping("/deposit")
    public String deposit(@Valid @ModelAttribute("operationForm") FinancialOperationForm form,
                          BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        return post(TransactionAction.DEPOSIT, form, result, model, redirectAttributes);
    }

    @PostMapping("/withdrawal")
    public String withdrawal(@Valid @ModelAttribute("operationForm") FinancialOperationForm form,
                             BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        return post(TransactionAction.WITHDRAWAL, form, result, model, redirectAttributes);
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("operationForm") FinancialOperationForm form,
                           BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        return post(TransactionAction.TRANSFER, form, result, model, redirectAttributes);
    }

    private String post(TransactionAction action, FinancialOperationForm form, BindingResult result, Model model,
                        RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            if (action == TransactionAction.TRANSFER && form.getDestinationAccountId() == null) {
                result.rejectValue("destinationAccountId", "required", "Choose a destination account");
            }
        }
        if (!result.hasErrors()) {
            try {
                switch (action) {
                    case DEPOSIT -> operations.deposit(form.getAccountId(), form.getAmount(), form.getCurrencyCode(), form.getIdempotencyKey());
                    case WITHDRAWAL -> operations.withdraw(form.getAccountId(), form.getAmount(), form.getCurrencyCode(), form.getIdempotencyKey());
                    case TRANSFER -> operations.transfer(form.getAccountId(), form.getDestinationAccountId(), form.getAmount(), form.getCurrencyCode(), form.getIdempotencyKey());
                }
                redirectAttributes.addFlashAttribute("transactionFeedback", action.feedback());
                return "redirect:/transactions";
            } catch (FinancialOperationException exception) {
                result.reject("operation.failed", safeMessage(exception));
            }
        }
        populateForm(model, form);
        model.addAttribute("action", action.name());
        model.addAttribute("selectedAction", action.name());
        model.addAttribute("formAction", "/transactions/" + action.name().toLowerCase(java.util.Locale.ROOT));
        model.addAttribute("currentEmail", org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName());
        return "transactions/new";
    }

    private String selectedAction(String requestedAction) {
        if (requestedAction == null) return TransactionAction.DEPOSIT.name();
        try {
            return TransactionAction.valueOf(requestedAction.toUpperCase(java.util.Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            return TransactionAction.DEPOSIT.name();
        }
    }

    private void populateForm(Model model, FinancialOperationForm form) {
        model.addAttribute("operationForm", form);
        model.addAttribute("accounts", accounts.listMine().stream().filter(FinancialAccount::active).toList());
    }

    private String selectedFilter(String requestedType) {
        if (requestedType == null) return "ALL";
        String normalized = requestedType.toUpperCase(java.util.Locale.ROOT);
        return normalized.equals("DEPOSIT") || normalized.equals("WITHDRAWAL") || normalized.equals("TRANSFER")
                ? normalized : "ALL";
    }

    private String safeMessage(FinancialOperationException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "We could not complete this operation. Review the details and try again." : message;
    }

    private enum TransactionAction {
        DEPOSIT("Deposit posted successfully."),
        WITHDRAWAL("Withdrawal posted successfully."),
        TRANSFER("Transfer posted successfully.");
        private final String feedback;
        TransactionAction(String feedback) { this.feedback = feedback; }
        String feedback() { return feedback; }
    }
}
