package org.scoula.backend.order.service.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OrderRejectedException extends BaseException {
	public OrderRejectedException(String message) {
		super(message, HttpStatus.BAD_REQUEST);
	}
}