package org.scoula.backend.order.service.exception;

public class PriceOutOfRangeException extends RuntimeException {

    public PriceOutOfRangeException() {
        super("주문 가격은 종가의 30% 범위 내에서만 설정할 수 있습니다");
    }
}
