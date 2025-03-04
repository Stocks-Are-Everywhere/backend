package org.scoula.backend.member.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class NotAuthorizedException extends BaseException {
    public NotAuthorizedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
