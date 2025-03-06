package org.scoula.backend.order.service.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OrderPriceQuotationException extends BaseException {
	public OrderPriceQuotationException(final String message) {
		super(message, HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
