package org.scoula.backend.order.service.orderbook;

import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.TradeOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class OrderStorage {

    private final Queue<TradeOrder> elements = new PriorityQueue<>(
            Comparator.comparing(TradeOrder::getCreatedDateTime)
                    .thenComparing(TradeOrder::getTotalQuantity, Comparator.reverseOrder())
    );

    public TradeHistoryResponse match(final TradeOrder incomingOrder) {
        final TradeOrder foundOrder = elements.peek();

        if (foundOrder == null || incomingOrder.hasSameAccount(foundOrder)) {
            return null;
        }

        final BigDecimal matchedQuantity = incomingOrder.calculateMatchQuantity(foundOrder);
        final BigDecimal matchPrice = foundOrder.getPrice(); // 체결 가격은 항상 기존 주문 가격

        // 1. 주문 수량 업데이트
        incomingOrder.decreaseRemainingQuantity(matchedQuantity);
        foundOrder.decreaseRemainingQuantity(matchedQuantity);

        // 2. 완전 체결된 주문 제거
        if (foundOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            elements.remove(foundOrder);
        }

        // TradeHistoryResponse 응답 생성 및 반환
        return createResponse(incomingOrder, foundOrder, matchedQuantity, matchPrice);
    }

    // 매도자, 매수자 구분 후 응답 생성
    private TradeHistoryResponse createResponse(final TradeOrder incomingOrder, final TradeOrder foundOrder, BigDecimal matchedQuantity, BigDecimal matchPrice) {
        if (incomingOrder.isSellType()) {
            return TradeHistoryResponse.builder()
                    .companyCode(incomingOrder.getCompanyCode())
                    .buyOrderId(foundOrder.getId())
                    .sellOrderId(incomingOrder.getId())
                    .quantity(matchedQuantity)
                    .price(matchPrice)
                    .tradeTime(Instant.now().getEpochSecond())
                    .build();
        }
        return TradeHistoryResponse.builder()
                .companyCode(incomingOrder.getCompanyCode())
                .buyOrderId(incomingOrder.getId())
                .sellOrderId(foundOrder.getId())
                .quantity(matchedQuantity)
                .price(matchPrice)
                .tradeTime(Instant.now().getEpochSecond())
                .build();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public void offer(TradeOrder order) {
        elements.offer(order);
    }

    public int size() {
        return elements.size();
    }

    public Collection<TradeOrder> getElements() {
        return Collections.unmodifiableCollection(elements);
    }
}
