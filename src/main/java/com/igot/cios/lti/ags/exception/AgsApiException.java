package com.igot.cios.lti.ags.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown by any AGS component (bearer-token validation, line item/score/result handling) to
 * signal a specific HTTP failure - caught by
 * {@link com.igot.cios.lti.ags.controller.AgsController} and translated into that HTTP status.
 */
@Getter
public class AgsApiException extends RuntimeException {
    private final HttpStatus status;

    public AgsApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
