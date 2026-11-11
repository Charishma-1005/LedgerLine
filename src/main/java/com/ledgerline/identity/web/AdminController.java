package com.ledgerline.identity.web;

import com.ledgerline.identity.application.AdminManagementService;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final AdminManagementService admins;
    public AdminController(AdminManagementService admins) {
        this.admins = admins;
    }

    @GetMapping({"", "/"})
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("currentEmail", authentication.getName());
        model.addAttribute("stats", admins.dashboardStats());
        model.addAttribute("events", admins.recentEvents());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(name = "q", defaultValue = "") String query,
                        @RequestParam(name = "page", defaultValue = "0") int requestedPage,
                        Authentication authentication, Model model) {
        int pageNumber = Math.min(Math.max(requestedPage, 0), 100_000);
        var page = admins.searchUsers(query, pageNumber, 25);
        model.addAttribute("users", page.users().stream().map(AdminUserView::from).toList());
        model.addAttribute("currentEmail", authentication.getName());
        model.addAttribute("query", query.length() > 254 ? query.substring(0, 254) : query);
        model.addAttribute("currentPage", page.page());
        model.addAttribute("totalPages", page.totalPages());
        model.addAttribute("totalUsers", page.totalElements());
        return "admin/users";
    }

    @PostMapping("/users/{userId}/enabled")
    public String setEnabled(@PathVariable @Positive long userId, @RequestParam boolean enabled,
                             @RequestParam(name = "q", defaultValue = "") String query,
                             @RequestParam(name = "page", defaultValue = "0") int page,
                             RedirectAttributes redirectAttributes) {
        admins.setUserEnabled(userId, enabled);
        redirectAttributes.addFlashAttribute("adminFeedback", enabled
                ? "User access has been enabled." : "User access has been disabled.");
        if (query != null && !query.isBlank()) redirectAttributes.addAttribute("q", query.substring(0, Math.min(254, query.length())));
        redirectAttributes.addAttribute("page", Math.max(0, Math.min(page, 100_000)));
        return "redirect:/admin/users";
    }
}
