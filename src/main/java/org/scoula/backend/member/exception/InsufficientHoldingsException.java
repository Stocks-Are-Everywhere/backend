package org.scoula.backend.member.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InsufficientHoldingsException extends BaseException {
	public InsufficientHoldingsException(final String message) {
		super(message, HttpStatus.BAD_REQUEST);
	}
}
