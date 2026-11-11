package com.ledgerline.identity.web;

import com.ledgerline.identity.application.DuplicateEmailException;
import com.ledgerline.identity.application.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IdentityController {
    private final RegistrationService registrationService;

    public IdentityController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "identity/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "identity/register";
        }
        try {
            registrationService.register(form.getEmail(), form.getPassword());
        } catch (DuplicateEmailException exception) {
            // Do not reveal whether an address is already registered.
            model.addAttribute("registrationError", "Unable to create an account with those details.");
            return "identity/register";
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "registered", required = false) String registered,
                        Model model) {
        if (error != null) {
            model.addAttribute("loginError", "Invalid email or password.");
        }
        if (registered != null) {
            model.addAttribute("registrationSuccess", "Account created. You can now sign in.");
        }
        return "identity/login";
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        model.addAttribute("email", authentication.getName());
        return "identity/account";
    }
}
