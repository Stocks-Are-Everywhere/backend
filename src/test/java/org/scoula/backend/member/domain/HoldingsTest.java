package org.scoula.backend.member.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.exception.HoldingsNotFoundException;
import org.scoula.backend.member.exception.InsufficientHoldingsException;
import org.scoula.backend.order.domain.Type;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingsTest {

    private Holdings holdings;

    @BeforeEach
    void setUp() {
        holdings = Holdings.builder()
                .quantity(BigDecimal.valueOf(100))
                .reservedQuantity(BigDecimal.valueOf(20))
                .averagePrice(BigDecimal.valueOf(100))
                .totalPurchasePrice(BigDecimal.valueOf(2000))
                .build();
    }

    @Test
    @DisplayName("보유 주식 수량이 충분할 경우 예외가 발생하지 않아야 한다")
    void validateEnoughHoldings_whenEnoughQuantity_shouldNotThrowException() {
        // Given
        BigDecimal checkQuantity = BigDecimal.valueOf(50);

        // When & Then
        assertThatCode(() -> holdings.validateEnoughHoldings(checkQuantity))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("보유 주식 수량이 부족할 경우 InsufficientHoldingsException이 발생해야 한다")
    void validateEnoughHoldings_whenNotEnoughQuantity_shouldThrowInsufficientHoldingsException() {
        // Given
        BigDecimal checkQuantity = BigDecimal.valueOf(90);

        // When & Then
        assertThatThrownBy(() -> holdings.validateEnoughHoldings(checkQuantity))
                .isInstanceOf(InsufficientHoldingsException.class)
                .hasMessage("판매 가능한 보유 주식 수량이 부족합니다.");
    }


    @Test
    @DisplayName("주식 수량이 0일 경우 validateExistHoldings 호출 시 HoldingsNotFoundException이 발생해야 한다")
    void validateExistHoldings_whenQuantityZero_shouldThrowHoldingsNotFoundException() {
        // Given
        holdings = Holdings.builder()
                .quantity(BigDecimal.valueOf(0))
                .reservedQuantity(BigDecimal.valueOf(20))
                .averagePrice(BigDecimal.valueOf(100))
                .totalPurchasePrice(BigDecimal.valueOf(2000))
                .build();

        // When & Then
        assertThatThrownBy(() -> holdings.validateExistHoldings())
                .isInstanceOf(HoldingsNotFoundException.class)
                .hasMessage("판매 가능한 보유 주식이 없습니다.");
    }

    @Test
    @DisplayName("예약 주문 처리 시 예약된 주식 수량이 업데이트 되어야 한다")
    void processReservedOrder_shouldUpdateReservedQuantity() {
        // Given
        BigDecimal reservedQuantity = BigDecimal.valueOf(10);

        // When
        holdings.processReservedOrder(reservedQuantity);

        // Then
        assertThat(holdings.getReservedQuantity()).isEqualTo(BigDecimal.valueOf(30));
    }

    @Test
    @DisplayName("매수 시 보유 주식 수량과 총 매수 금액이 업데이트 되어야 한다")
    void updateHoldings_whenBuy_shouldUpdateQuantityAndTotalPurchasePrice() {
        // Given
        BigDecimal updatePrice = BigDecimal.valueOf(2000);
        BigDecimal updateQuantity = BigDecimal.valueOf(10);

        // When
        holdings.updateHoldings(Type.BUY, updatePrice, updateQuantity);

        // Then
        assertThat(holdings.getQuantity()).isEqualTo(BigDecimal.valueOf(110));
        assertThat(holdings.getTotalPurchasePrice()).isEqualTo(BigDecimal.valueOf(22000));
        assertThat(holdings.getAveragePrice()).isEqualTo(BigDecimal.valueOf(2000000, 4));
    }

    @Test
    @DisplayName("매도 시 보유 주식 수량과 총 매수 금액이 업데이트 되어야 한다")
    void updateHoldings_whenSell_shouldUpdateQuantityAndTotalPurchasePrice() {
        // Given
        BigDecimal updateQuantity = BigDecimal.valueOf(1);
        BigDecimal originalTotalQuantity = holdings.getTotalPurchasePrice();

        // When
        holdings.updateHoldings(Type.SELL, BigDecimal.ZERO, updateQuantity);

        // Then
        assertThat(holdings.getQuantity()).isEqualTo(BigDecimal.valueOf(99));
        assertThat(holdings.getTotalPurchasePrice()).isEqualTo(originalTotalQuantity.subtract(updateQuantity.multiply(holdings.getAveragePrice())));
    }

    @Test
    @DisplayName("보유한 주식을 모두 판매한 경우 삭제한다.")
    void deleteHoldings() {
        // given
        BigDecimal quantity = BigDecimal.valueOf(100);

        // when
        holdings.updateHoldings(Type.SELL, BigDecimal.ZERO, quantity);

        // then
        assertThat(holdings.getDeletedDateTime()).isNotNull();
    }
}