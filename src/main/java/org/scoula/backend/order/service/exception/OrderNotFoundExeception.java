package org.scoula.backend.order.service.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OrderNotFoundExeception extends BaseException {

    public OrderNotFoundExeception() {
        super("주문 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
}
