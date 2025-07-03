package com.dev.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ForbiddenException extends ResponseStatusException {

    public ForbiddenException(String errorMessage) {
        super(HttpStatus.FORBIDDEN, errorMessage, new Throwable(errorMessage));
    }
}
