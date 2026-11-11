package com.ledgerline.auditor.web;

import com.ledgerline.auditor.application.AuditorReadService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auditor")
public class AuditorController {
    private final AuditorReadService auditor;

    public AuditorController(AuditorReadService auditor) {
        this.auditor = auditor;
    }

    @GetMapping({"", "/"})
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("currentEmail", authentication.getName());
        model.addAttribute("events", auditor.recentAuditEvents());
        model.addAttribute("statementLines", auditor.recentStatementLines());
        return "auditor/dashboard";
    }
}
