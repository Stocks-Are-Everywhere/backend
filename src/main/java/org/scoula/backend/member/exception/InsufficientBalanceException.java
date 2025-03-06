package org.scoula.backend.member.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BaseException {
	public InsufficientBalanceException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

