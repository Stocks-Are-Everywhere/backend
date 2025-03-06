package org.scoula.backend.member.exception;

import org.springframework.http.HttpStatus;

// TODO: modify after implementing the global exception handler
public class InsufficientBalanceException extends IllegalArgumentException {
	private final HttpStatus status;

	public InsufficientBalanceException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }
}

