package org.scoula.backend.order.service.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class MatchingException extends BaseException {
	public MatchingException(String message) {
		super(message, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	// 예외 던지기 -> tracking 과정을 없애 비용 절감
	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}

}
