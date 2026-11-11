package com.ledgerline.identity.web;

import com.ledgerline.identity.application.AdminManagementException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = AdminController.class)
public class AdminExceptionHandler {
    @ExceptionHandler(AdminManagementException.class)
    public ModelAndView conflict(AdminManagementException exception, Authentication authentication) {
        ModelAndView view = new ModelAndView("admin/action-error");
        view.setStatus(HttpStatus.CONFLICT);
        view.addObject("currentEmail", authentication.getName());
        view.addObject("adminError", exception.getMessage());
        return view;
    }
}
