package org.scoula.backend.fake;

import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.service.TradeHistoryRepository;

import java.util.ArrayList;
import java.util.List;

public class TestTradeHistoryRepository implements TradeHistoryRepository {

    private final List<TradeHistory> elements = new ArrayList<>();

    @Override
    public TradeHistory save(TradeHistory tradeHistory) {
        if (hasElement(tradeHistory)) {
            elements.remove(elements.indexOf(tradeHistory));
            elements.add(tradeHistory);
            return tradeHistory;
        }
        TradeHistory saved = TradeHistory.builder()
                .id((long) (elements.size() + 1))
                .companyCode(tradeHistory.getCompanyCode())
                .sellOrderId(tradeHistory.getSellOrderId())
                .buyOrderId(tradeHistory.getBuyOrderId())
                .price(tradeHistory.getPrice())
                .quantity(tradeHistory.getQuantity())
                .tradeTime(tradeHistory.getTradeTime())
                .build();
        elements.add(tradeHistory);
        return saved;
    }

    private boolean hasElement(TradeHistory tradeHistory) {
        return elements.stream()
                .anyMatch(h -> h.getId().equals(tradeHistory.getId()));
    }

    @Override
    public List<String> findDistinctCompanyCodes() {
        return List.of();
    }

    @Override
    public List<TradeHistory> findRecentTradesByCompanyCode(String companyCode, int limit) {
        return List.of();
    }

    @Override
    public List<TradeHistory> getTradeHistory() {
        return List.of();
    }
}
