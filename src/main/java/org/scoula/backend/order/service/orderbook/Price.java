package org.scoula.backend.order.service.orderbook;

import java.math.BigDecimal;

public class Price implements Comparable<Price> {

    private final BigDecimal value;

    public Price(BigDecimal value) {
        this.value = value;
    }

    @Override
    public int compareTo(Price o) {
        return this.value.compareTo(o.value);
    }

    public boolean isHigherThan(BigDecimal price) {
        return this.value.compareTo(price) > 0;
    }

    public BigDecimal getValue() {
        return value;
    }
}
