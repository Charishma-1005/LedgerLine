package com.ledgerline.reporting.web;

import com.ledgerline.reporting.application.ReportsQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Controller
public class ReportsController {
    private final ReportsQueryService reports;

    public ReportsController(ReportsQueryService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports")
    public String overview(@RequestParam(name = "start", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedStart,
                           @RequestParam(name = "end", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedEnd,
                           Authentication authentication, Model model) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate defaultEnd = today;
        LocalDate defaultStart = today.minusMonths(11).withDayOfMonth(1);
        LocalDate start = requestedStart == null ? defaultStart : requestedStart;
        LocalDate end = requestedEnd == null ? defaultEnd : requestedEnd;
        if (start.isAfter(end) || end.equals(LocalDate.MAX)) {
            model.addAttribute("reportError", "Choose a valid date range; showing the last 12 months.");
            start = defaultStart;
            end = defaultEnd;
        }
        model.addAttribute("report", reports.load(start, end));
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("currentEmail", authentication.getName());
        return "reporting/overview";
    }
}
