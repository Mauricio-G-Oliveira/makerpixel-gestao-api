package com.makerpixel.contabil.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConciliacaoConflictException extends RuntimeException {

    public ConciliacaoConflictException(String message) {
        super(message);
    }
}
