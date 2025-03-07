package org.scoula.backend.order.service.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.order.service.exception.OrderPriceQuotationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderValidatorTest {

    @Test
    @DisplayName("가격에 맞는 OrderValidator가 반환된다.")
    void getUnitByPrice() {
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(0))).isEqualTo(OrderValidator.UNIT_1);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(1000))).isEqualTo(OrderValidator.UNIT_1);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(2000))).isEqualTo(OrderValidator.UNIT_5);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(7500))).isEqualTo(OrderValidator.UNIT_10);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(25000))).isEqualTo(OrderValidator.UNIT_50);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(100000))).isEqualTo(OrderValidator.UNIT_100);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(300000))).isEqualTo(OrderValidator.UNIT_500);
        assertThat(OrderValidator.getUnitByPrice(new BigDecimal(700000))).isEqualTo(OrderValidator.UNIT_1000);
    }

    @Test
    @DisplayName("입력이 음수일 경우 예외가 발생한다.")
    void getUnitByInvalidPrice() {
        assertThatThrownBy(() -> OrderValidator.getUnitByPrice(new BigDecimal("-1000")))
                .isInstanceOf(OrderPriceQuotationException.class);
    }

    @Test
    @DisplayName("유효한 가격을 입력할 경우 정상 처리된다.")
    void isValidPrice() {
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_1.isValidPrice(new BigDecimal(1_001)));
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_5.isValidPrice(new BigDecimal(2_005)));
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_10.isValidPrice(new BigDecimal(5_010)));
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_50.isValidPrice(new BigDecimal(20_050)));
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_100.isValidPrice(new BigDecimal(50_100)));
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_500.isValidPrice(new BigDecimal(200_500)));
        assertThatNoException().isThrownBy(() -> OrderValidator.UNIT_1000.isValidPrice(new BigDecimal(501_000)));
    }

    @Test
    @DisplayName("잘못된 가격이 예외를 발생시키는지 확인")
    void isValidInvalidPrice() {
        BigDecimal priceUnit1 = new BigDecimal("0.5");
        BigDecimal priceUnit5 = new BigDecimal(2_001);
        BigDecimal priceUnit10 = new BigDecimal(5_005);
        BigDecimal priceUnit50 = new BigDecimal(20_010);
        BigDecimal priceUnit100 = new BigDecimal(50_050);
        BigDecimal priceUnit500 = new BigDecimal(200_100);
        BigDecimal priceUnit1000 = new BigDecimal(500_500);

        assertThatThrownBy(() -> OrderValidator.UNIT_1.isValidPrice(priceUnit1)).isInstanceOf(OrderPriceQuotationException.class);
        assertThatThrownBy(() -> OrderValidator.UNIT_5.isValidPrice(priceUnit5)).isInstanceOf(OrderPriceQuotationException.class);
        assertThatThrownBy(() -> OrderValidator.UNIT_10.isValidPrice(priceUnit10)).isInstanceOf(OrderPriceQuotationException.class);
        assertThatThrownBy(() -> OrderValidator.UNIT_50.isValidPrice(priceUnit50)).isInstanceOf(OrderPriceQuotationException.class);
        assertThatThrownBy(() -> OrderValidator.UNIT_100.isValidPrice(priceUnit100)).isInstanceOf(OrderPriceQuotationException.class);
        assertThatThrownBy(() -> OrderValidator.UNIT_500.isValidPrice(priceUnit500)).isInstanceOf(OrderPriceQuotationException.class);
        assertThatThrownBy(() -> OrderValidator.UNIT_1000.isValidPrice(priceUnit1000)).isInstanceOf(OrderPriceQuotationException.class);
    }
}
