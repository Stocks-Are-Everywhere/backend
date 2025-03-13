package org.scoula.backend.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.exception.InsufficientBalanceException;
import org.scoula.backend.order.domain.Type;

public class AccountTest {

    private final Member member = Member.builder()
            .email("test@example.com")
            .username("testuser")
            .build();

    private Account account;

    @BeforeEach
    void setUp() {
        member.createAccount();
        account = member.getAccount();
    }

    @Test
    @DisplayName("초기 금액을 1억으로 설정한다.")
    void setInitialPrice() {
        // given
        member.createAccount();
        Account account = member.getAccount();

        // when
        BigDecimal balance = account.getBalance();

        // then
        assertThat(balance).isEqualTo(new BigDecimal("100000000"));
    }
    @Test
    @DisplayName("Throw exception when balance is not enough for the amount")
    void throwExceptionWhenBalanceIsNotEnough() {
        // given
        assert account.getBalance().equals(new BigDecimal("100000000")) : "Balance is not initialized(100000000)";
        BigDecimal amountToBuy = new BigDecimal("100000001");

        // when & then
        assertThatThrownBy(() -> account.validateDepositBalance(amountToBuy))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("주문금액")
                .hasMessageContaining("예수금잔액")
                .hasMessageContaining("초과");
    }

    @Test
    @DisplayName("Successfully process buy order when balance is sufficient")
    void successfullyProcessBuyOrder() {
        // given
        BigDecimal priceToBuy = new BigDecimal("30000000");
        BigDecimal quantityToBuy = new BigDecimal("1");
        BigDecimal expectedBalance = new BigDecimal("70000000");

        // when
        account.processOrder(Type.BUY, priceToBuy, quantityToBuy);

        // then
        assertThat(account.getBalance()).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("Throw exception when processing buy order with insufficient balance")
    void throwExceptionWhenProcessingBuyOrderWithInsufficientBalance() {
        // given
        BigDecimal priceToBuy = new BigDecimal("150000000");
        BigDecimal quantityToBuy = new BigDecimal("1");

        // when & then
        assertThatThrownBy(() -> account.processOrder(Type.BUY, priceToBuy, quantityToBuy))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("주문금액")
                .hasMessageContaining("예수금잔액")
                .hasMessageContaining("초과");
    }

    @Test
    @DisplayName("Successfully process sell order")
    void successfullyProcessSellOrder() {
        // given
        BigDecimal priceToSell = new BigDecimal("30000000");
        BigDecimal quantityToSell = new BigDecimal("1");
        BigDecimal expectedBalance = new BigDecimal("130000000");

        // when
        account.processOrder(Type.SELL, priceToSell, quantityToSell);

        // then
        assertThat(account.getBalance()).isEqualTo(expectedBalance);
    }

}