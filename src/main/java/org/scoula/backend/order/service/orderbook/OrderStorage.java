package org.scoula.backend.order.service.orderbook;

import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class OrderStorage {

    private final Queue<Order> elements = new PriorityQueue<>(
            Comparator.comparing(Order::getTimestamp)
                    .thenComparing(Order::getTotalQuantity, Comparator.reverseOrder())
    );

    public TradeHistoryResponse match(final Order incomingOrder) {
        final Order foundOrder = elements.peek();

        if (incomingOrder.getAccount().getMember().equals(foundOrder.getAccount().getMember())) {
            return null;
        }

        final BigDecimal matchedQuantity = incomingOrder.getRemainingQuantity()
                .min(foundOrder.getRemainingQuantity());
        final BigDecimal matchPrice = foundOrder.getPrice(); // 체결 가격은 항상 기존 주문 가격

        // 1. 주문 수량 업데이트
        incomingOrder.decreaseRemainingQuantity(matchedQuantity);
        foundOrder.decreaseRemainingQuantity(matchedQuantity);

        // 2. 매수자/매도자 결정
        Order buyOrder, sellOrder;
        if (incomingOrder.isSellType()) {
            buyOrder = foundOrder;
            sellOrder = incomingOrder;
        } else {
            buyOrder = incomingOrder;
            sellOrder = foundOrder;
        }

        // 4. 완전 체결된 주문 제거
        if (foundOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            elements.remove(foundOrder);
        }

        // 체결 내역 반환
        return TradeHistoryResponse.builder()
                .companyCode(buyOrder.getCompanyCode())
                .buyOrderId(buyOrder.getId())
                .sellOrderId(sellOrder.getId())
                .quantity(matchedQuantity)
                .price(matchPrice)
                .tradeTime(Instant.now().getEpochSecond())
                .build();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public void offer(Order order) {
        elements.offer(order);
    }

    public int size() {
        return elements.size();
    }

    public Collection<Order> getElements() {
        return Collections.unmodifiableCollection(elements);
    }
}
