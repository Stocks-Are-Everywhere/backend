package org.scoula.backend.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.service.exception.MatchingException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class OrderBookServiceTest {

    private OrderBookService orderBookService;
    private TradeHistoryService tradeHistoryService;

    @BeforeEach
    void setUp() {
        tradeHistoryService = Mockito.mock(TradeHistoryService.class);
        orderBookService = new OrderBookService("005930", tradeHistoryService);
    }


    @Test
    @DisplayName("매수 주문의 경우 높은 가격의 주문부터 체결된다.")
    void buyOrderHigherPricePriorityMatching() throws MatchingException {
        LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
        Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(2000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(0), new BigDecimal(10), createdAt, OrderStatus.MARKET);

        orderBookService.received(buyOrder1);
        orderBookService.received(buyOrder2);
        orderBookService.received(sellOrder);

        assertThat(buyOrder1.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(buyOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
        assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("매도 주문의 경우 낮은 가격의 주문부터 체결된다.")
    void sellOrderLowerPricePriorityMatching() throws MatchingException {
        LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
        Order sellOrder1 = createOrder(1L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order sellOrder2 = createOrder(2L, Type.SELL, new BigDecimal(2000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order buyOrder = createOrder(3L, Type.BUY, new BigDecimal(0), new BigDecimal(10), createdAt, OrderStatus.MARKET);

        orderBookService.received(sellOrder1);
        orderBookService.received(sellOrder2);
        orderBookService.received(buyOrder);

        assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
        assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("매수 주문시, 같은 가격일 경우 먼저 주문이 들어온 주문부터 처리한다.")
    void buyOrderTimePriorityMatching() throws MatchingException {
        LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
        Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt.plusSeconds(1), OrderStatus.ACTIVE);
        Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.MARKET);
        orderBookService.received(buyOrder1);
        orderBookService.received(buyOrder2);
        orderBookService.received(sellOrder);

        assertThat(buyOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
        assertThat(buyOrder2.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("매도 주문시, 같은 가격일 경우 먼저 주문이 들어온 주문부터 처리한다.")
    void sellOrderTimePriorityMatching() throws MatchingException {
        LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
        Order sellOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt.plusSeconds(1), OrderStatus.ACTIVE);
        Order sellOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order buyOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.MARKET);

        orderBookService.received(sellOrder1);
        orderBookService.received(sellOrder2);
        orderBookService.received(buyOrder);

        assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
        assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
    }


    @Test
    @DisplayName("매수 주문시, 모든 조건이 일치할 경우 수량이 많은 주문부터 체결한다.")
    void buyOrderQuantityPriorityMatching() throws MatchingException {
        LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
        Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(11), createdAt, OrderStatus.ACTIVE);
        Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.MARKET);

        orderBookService.received(buyOrder1);
        orderBookService.received(buyOrder2);
        orderBookService.received(sellOrder);

        assertThat(buyOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
        assertThat(buyOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(1));
        assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("매도 주문시, 모든 조건이 일치할 경우 수량이 많은 주문부터 체결한다.")
    void sellOrderQuantityPriorityMatching() throws MatchingException {
        LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
        Order sellOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.ACTIVE);
        Order sellOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(11), createdAt, OrderStatus.ACTIVE);
        Order buyOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt, OrderStatus.MARKET);

        orderBookService.received(sellOrder1);
        orderBookService.received(sellOrder2);
        orderBookService.received(buyOrder);

        assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
        assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(1));
        assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    private Order createOrder(Long id, Type type, BigDecimal price, BigDecimal quantity, LocalDateTime timestamp, OrderStatus status) {
        return Order.builder()
                .id(id)
                .companyCode("005930")
                .type(type)
                .totalQuantity(quantity)
                .remainingQuantity(quantity)
                .status(OrderStatus.ACTIVE)
                .price(price)
                .timestamp(timestamp)
                .status(status)
                .build();
    }
}