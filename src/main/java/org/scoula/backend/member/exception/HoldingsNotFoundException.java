package org.scoula.backend.member.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class HoldingsNotFoundException extends BaseException {
    public HoldingsNotFoundException(final String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
