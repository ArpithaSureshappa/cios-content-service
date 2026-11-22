package com.igot.cios.lti.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Failure while processing a Tool's Deep Linking response. Carries the HTTP status the caller
 * should see; the message is always a fixed, safe text (never the raw JWT or an internal error).
 */
@Getter
public class LtiReturnException extends RuntimeException {

    private final HttpStatus status;

    public LtiReturnException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

}
