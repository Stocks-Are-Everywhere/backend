package org.scoula.backend.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.exception.InsufficientBalanceException;

public class AccountTest {

    private final Member member = Member.builder()
            .email("test@example.com")
            .username("testuser")
            .build();

    private final Account account = new Account(member);  

    @Test
    @DisplayName("Return true when balance is enough for the amount")
    void returnTrueWhenBalanceIsEnough() {
        // given
        assert account.getBalance().equals(new BigDecimal("100000000")) : "Balance is not initialized(100000000)";
        BigDecimal amountToBuy = new BigDecimal("30000000");

        // when
        boolean hasEnough = account.hasEnoughBalance(amountToBuy);

        // then
        assertThat(hasEnough).isTrue();
    }

    @Test
    @DisplayName("Return false when balance is not enough for the amount")
    void returnFalseWhenBalanceIsNotEnough() {
        // given
        assert account.getBalance().equals(new BigDecimal("100000000")) : "Balance is not initialized(100000000)";
        BigDecimal amountToBuy = new BigDecimal("100000001");

        // when
        boolean hasEnough = account.hasEnoughBalance(amountToBuy);

        // then
        assertThat(hasEnough).isFalse();
    }

    @Test
    @DisplayName("Successfully process buy order when balance is sufficient")
    void successfullyProcessBuyOrder() {
        // given
        BigDecimal amountToBuy = new BigDecimal("30000000");
        BigDecimal expectedBalance = new BigDecimal("70000000");

        // when
        account.processBuyOrder(amountToBuy);

        // then
        assertThat(account.getBalance()).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("Throw exception when processing buy order with insufficient balance")
    void throwExceptionWhenProcessingBuyOrderWithInsufficientBalance() {
        // given
        BigDecimal amountToBuy = new BigDecimal("150000000");

        // when & then
        assertThatThrownBy(() -> account.processBuyOrder(amountToBuy))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("주문금액")
                .hasMessageContaining("예수금잔액")
                .hasMessageContaining("초과");
    }

    @Test
    @DisplayName("Successfully process sell order")
    void successfullyProcessSellOrder() {
        // given
        BigDecimal amountToSell = new BigDecimal("30000000");
        BigDecimal expectedBalance = new BigDecimal("130000000");

        // when
        account.processSellOrder(amountToSell);

        // then
        assertThat(account.getBalance()).isEqualTo(expectedBalance);
    }

}