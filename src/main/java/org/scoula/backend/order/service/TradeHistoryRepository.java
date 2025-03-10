package org.scoula.backend.order.service;

import org.scoula.backend.order.domain.TradeHistory;

import java.util.List;

public interface TradeHistoryRepository {

    void save(final TradeHistory tradeHistory);

    /**
     * 모든 고유 회사 코드 조회
     */
    List<String> findDistinctCompanyCodes();

    /**
     * 특정 회사의 최근 N개 거래 내역 조회
     */
    List<TradeHistory> findRecentTradesByCompanyCode(String companyCode, int limit);

    List<TradeHistory> getTradeHistory();
}
