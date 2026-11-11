package com.ledgerline.account.web;

import com.ledgerline.account.application.FinancialAccountClosedException;
import com.ledgerline.account.application.FinancialAccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = FinancialAccountController.class)
public class FinancialAccountExceptionHandler {
    @ExceptionHandler(FinancialAccountNotFoundException.class)
    public ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(FinancialAccountClosedException.class)
    public ResponseEntity<Void> closed() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
