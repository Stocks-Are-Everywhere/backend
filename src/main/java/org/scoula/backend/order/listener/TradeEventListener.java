package org.scoula.backend.order.listener;

import lombok.RequiredArgsConstructor;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.service.OrderRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TradeEventListener {

    private final AccountService accountService;
    private final StockHoldingsService stockHoldingsService;
    private final OrderRepository orderRepository;

    @Async
    @EventListener
    @Transactional
    public void createTradeHistoryEvent(final TradeHistory tradeHistory) {
        // 1. 주문 내역 조회
        Order buyOrder = orderRepository.getById(tradeHistory.getBuyOrderId());
        Order sellOrder = orderRepository.getById(tradeHistory.getSellOrderId());

        buyOrder.decreaseRemainingQuantity(tradeHistory.getQuantity());
        sellOrder.decreaseRemainingQuantity(tradeHistory.getQuantity());

        orderRepository.save(buyOrder);
        orderRepository.save(sellOrder);

        // 2. 계좌 잔액 처리
        accountService.updateAccountAfterTrade(buyOrder.getMemberId(), Type.BUY, tradeHistory.getPrice(), tradeHistory.getQuantity());
        accountService.updateAccountAfterTrade(sellOrder.getMemberId(), Type.SELL, tradeHistory.getPrice(), tradeHistory.getQuantity());

        // 3. 보유 주식 처리
        stockHoldingsService.updateHoldingsAfterTrade(Type.BUY, buyOrder.getAccount(), tradeHistory.getCompanyCode(), tradeHistory.getPrice(), tradeHistory.getQuantity());
        stockHoldingsService.updateHoldingsAfterTrade(Type.SELL, sellOrder.getAccount(), tradeHistory.getCompanyCode(), tradeHistory.getPrice(), tradeHistory.getQuantity());
    }
}
