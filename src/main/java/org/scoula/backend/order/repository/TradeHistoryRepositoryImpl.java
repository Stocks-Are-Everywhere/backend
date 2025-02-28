package org.scoula.backend.order.repository;

import java.util.List;

import org.scoula.backend.order.domain.TradeHistory;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TradeHistoryRepositoryImpl {
	private final TradeHistoryJpaRepository tradeHistoryJpaRepository;

	public void save(final TradeHistory tradeHistory) {
		tradeHistoryJpaRepository.save(tradeHistory);
	}

	public List<TradeHistory> getTradeHistory() {
		return tradeHistoryJpaRepository.findAll();
	}
}
