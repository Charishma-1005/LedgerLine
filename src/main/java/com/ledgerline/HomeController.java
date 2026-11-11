package com.ledgerline;

import com.ledgerline.dashboard.application.DashboardQueryService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final DashboardQueryService dashboard;

    public HomeController(DashboardQueryService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_AUDITOR".equals(authority.getAuthority()))) {
                return "redirect:/auditor";
            }
            model.addAttribute("dashboard", dashboard.loadForCurrentUser());
        }
        return "index";
    }
}
