package org.scoula.backend.order.repository;

import java.util.List;

import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.service.TradeHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TradeHistoryRepositoryImpl implements TradeHistoryRepository {

	private final TradeHistoryJpaRepository tradeHistoryJpaRepository;

	@Override
	public TradeHistory save(final TradeHistory tradeHistory) {
		return tradeHistoryJpaRepository.save(tradeHistory);
	}

	@Override
	public List<String> findDistinctCompanyCodes() {
		return tradeHistoryJpaRepository.findDistinctCompanyCodes();
	}

	@Override
	public List<TradeHistory> findRecentTradesByCompanyCode(String companyCode, int limit) {
		return tradeHistoryJpaRepository.findRecentTradesByCompanyCode(companyCode, PageRequest.of(0, limit));
	}

	@Override
	public List<TradeHistory> getTradeHistory() {
		return tradeHistoryJpaRepository.findAll();
	}

}
