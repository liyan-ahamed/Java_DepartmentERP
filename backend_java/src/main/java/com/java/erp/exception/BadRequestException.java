package com.java.erp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Exception thrown when a request contains invalid or conflicting data.
 * May carry a list of individual errors (e.g. one per invalid row of a bulk request).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    private final List<String> errors;

    public BadRequestException(String message) {
        super(message);
        this.errors = List.of();
    }

    public BadRequestException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of();
    }

    public List<String> getErrors() {
        return errors;
    }
}
