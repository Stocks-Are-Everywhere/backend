package org.scoula.backend.order.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class TradeOrderTest {

    private final Member member = Member.builder()
            .id(1L)
            .email("test@example.com")
            .username("testuser")
            .build();

    private Account account;

    private final Member member2 = Member.builder()
            .id(2L)
            .email("test2@example.com")
            .username("testuser2")
            .build();

    private Account account2;

    @BeforeEach
    void setUp() {
        member.createAccount();
        account = new Account(1L, new BigDecimal(10), new BigDecimal(10), member, 0L);
        member2.createAccount();
        account2 = new Account(2L, new BigDecimal(10), new BigDecimal(10), member2, 0L);
    }

    @Test
    @DisplayName("주문이 동일한 계정에서 왔으면 true를 반환한다.")
    void hasSameAccountWhenSameAccount() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account);
        TradeOrder buyOrder = createTradeOrder(Type.BUY, account);

        // when
        boolean result = sellOrder.hasSameAccount(buyOrder);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("주문이 다른 계정에서 왔을 때 false를 반환한다.")
    void hasSameAccountWhenDifferentAccount() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account);
        TradeOrder buyOrder = createTradeOrder(Type.BUY, account2);

        // when
        boolean result = sellOrder.hasSameAccount(buyOrder);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("입력받은 주문의 account가 null일 경우 false를 반환한다.")
    void hasSameAccountWhenNullAccount() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account);
        TradeOrder buyOrder = new TradeOrder(1L, "005930", Type.BUY, OrderStatus.COMPLETE,
                new BigDecimal(10), new BigDecimal(10), new BigDecimal(10), LocalDateTime.now(), null);

        // when
        boolean result1 = sellOrder.hasSameAccount(buyOrder);
        boolean result2 = sellOrder.hasSameAccount(null);

        // then
        assertThat(result1).isFalse();
        assertThat(result2).isFalse();
    }

    @Test
    @DisplayName("남은 양 감소 후 상태가 완료로 변경되어야 한다")
    void completeOrder() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account);

        // when
        sellOrder.decreaseRemainingQuantity(sellOrder.getRemainingQuantity());

        // then
        assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(sellOrder.getStatus()).isEqualTo(OrderStatus.COMPLETE);
    }

    @Test
    @DisplayName("remaining quantity가 감소 후에도 남아있을 경우 Active 상태를 유지한다.")
    void notCompleteOrder() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account);

        // when
        sellOrder.decreaseRemainingQuantity(sellOrder.getRemainingQuantity().add(new BigDecimal(1)));

        // then
        assertThat(sellOrder.getStatus()).isEqualTo(OrderStatus.ACTIVE);
    }

    @Test
    @DisplayName("매도 주문일 경우 true를 반환한다.")
    void isSellType_whenSellOrder_shouldReturnTrue() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account);

        // when
        boolean result = sellOrder.isSellType();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("매도 주문이 아닐 때는 false를 반환한다.")
    void isSellType_whenNotSellOrder_shouldReturnFalse() {
        // given
        TradeOrder buyOrder = createTradeOrder(Type.BUY, account);

        // when & then
        assertThat(buyOrder.isSellType()).isFalse();
    }

    @Test
    @DisplayName("주문끼리 매칭할 수 있는 최대 수량을 계산한다.")
    void calculateMatchQuantity_whenOrdersMatch_shouldReturnCorrectMatchQuantity() {
        // given
        TradeOrder sellOrder = createTradeOrder(Type.SELL, account, new BigDecimal(10));
        TradeOrder buyOrder = createTradeOrder(Type.BUY, account2, new BigDecimal(5));

        // when
        BigDecimal matchQuantity = sellOrder.calculateMatchQuantity(buyOrder);

        // then
        assertThat(matchQuantity).isEqualTo(BigDecimal.valueOf(5));
    }

    TradeOrder createTradeOrder(Type type, Account account) {
        return TradeOrder.builder()
                .id(1L)
                .companyCode("A123")
                .type(type)
                .status(OrderStatus.ACTIVE)
                .totalQuantity(BigDecimal.valueOf(100))
                .remainingQuantity(BigDecimal.valueOf(100))
                .price(BigDecimal.valueOf(50))
                .createdDateTime(LocalDateTime.now())
                .account(account)
                .build();
    }

    TradeOrder createTradeOrder(Type type, Account account, BigDecimal totalQuantity) {
        return TradeOrder.builder()
                .id(1L)
                .companyCode("A123")
                .type(type)
                .status(OrderStatus.ACTIVE)
                .totalQuantity(totalQuantity)
                .remainingQuantity(totalQuantity)
                .price(BigDecimal.valueOf(50))
                .createdDateTime(LocalDateTime.now())
                .account(account)
                .build();
    }
}
