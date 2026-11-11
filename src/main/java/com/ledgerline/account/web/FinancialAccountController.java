package com.ledgerline.account.web;

import com.ledgerline.account.application.FinancialAccountClosedException;
import com.ledgerline.account.application.FinancialAccountService;
import com.ledgerline.account.application.FinancialAccountOverviewService;
import com.ledgerline.account.application.InvalidCurrencyCodeException;
import com.ledgerline.account.application.FinancialAccountOverview;
import com.ledgerline.account.domain.FinancialAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class FinancialAccountController {
    private final FinancialAccountService accounts;
    private final FinancialAccountOverviewService overview;

    public FinancialAccountController(FinancialAccountService accounts, FinancialAccountOverviewService overview) {
        this.accounts = accounts;
        this.overview = overview;
    }

    @GetMapping("/accounts")
    public String list(Authentication authentication, Model model) {
        List<FinancialAccountView> views = overview.listMine().stream()
                .map(this::toView).toList();
        model.addAttribute("accounts", views);
        model.addAttribute("currentEmail", authentication.getName());
        return "accounts/list";
    }

    @GetMapping("/accounts/new")
    public String newAccount(Authentication authentication, Model model) {
        model.addAttribute("accountForm", new CreateFinancialAccountForm());
        model.addAttribute("accountTypes", com.ledgerline.account.domain.FinancialAccountType.values());
        model.addAttribute("currentEmail", authentication.getName());
        return "accounts/new";
    }

    @PostMapping("/accounts")
    public String create(@Valid @ModelAttribute("accountForm") CreateFinancialAccountForm form,
                         BindingResult bindingResult, Authentication authentication,
                         Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                FinancialAccount created = accounts.create(form.getDisplayName(), form.getType(), form.getCurrencyCode());
                redirectAttributes.addFlashAttribute("accountFeedback", "Account created successfully.");
                return "redirect:/accounts/" + created.id();
            } catch (InvalidCurrencyCodeException exception) {
                bindingResult.rejectValue("currencyCode", "currency.unsupported", exception.getMessage());
            }
        }
        model.addAttribute("accountTypes", com.ledgerline.account.domain.FinancialAccountType.values());
        model.addAttribute("currentEmail", authentication.getName());
        return "accounts/new";
    }

    @GetMapping("/accounts/{accountId}")
    public String details(@PathVariable long accountId, Authentication authentication, Model model) {
        addAccountToModel(overview.getMine(accountId), model);
        model.addAttribute("currentEmail", authentication.getName());
        return "accounts/detail";
    }

    @PostMapping("/accounts/{accountId}/name")
    public String rename(@PathVariable long accountId,
                         @Valid @ModelAttribute("nameForm") UpdateDisplayNameForm form,
                         BindingResult bindingResult, Authentication authentication,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addAccountToModel(overview.getMine(accountId), model);
            model.addAttribute("currentEmail", authentication.getName());
            return "accounts/detail";
        }
        accounts.renameMine(accountId, form.getDisplayName());
        redirectAttributes.addFlashAttribute("accountFeedback", "Display name updated.");
        return "redirect:/accounts/" + accountId;
    }

    @PostMapping("/accounts/{accountId}/close")
    public String close(@PathVariable long accountId, RedirectAttributes redirectAttributes) {
        accounts.closeMine(accountId);
        redirectAttributes.addFlashAttribute("accountFeedback", "Account closed. Its history has been kept.");
        return "redirect:/accounts/" + accountId;
    }

    private void addAccountToModel(FinancialAccountOverview accountOverview, Model model) {
        FinancialAccountView view = toView(accountOverview);
        model.addAttribute("account", view);
        model.addAttribute("nameForm", new UpdateDisplayNameForm(view.displayName()));
    }

    private FinancialAccountView toView(FinancialAccountOverview accountOverview) {
        return FinancialAccountView.from(accountOverview.account(), accountOverview.ledgerBalance());
    }
}
