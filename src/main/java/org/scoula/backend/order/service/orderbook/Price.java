package org.scoula.backend.order.service.orderbook;

import java.math.BigDecimal;
import java.util.Objects;

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

    public boolean isLowerThan(BigDecimal price) {
        return this.value.compareTo(price) < 0;
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Price price = (Price) o;
        return Objects.equals(value, price.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
