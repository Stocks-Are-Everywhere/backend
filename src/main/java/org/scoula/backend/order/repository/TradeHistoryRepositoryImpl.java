package org.scoula.backend.order.repository;

import java.util.List;

import org.scoula.backend.order.domain.TradeHistory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TradeHistoryRepositoryImpl {
	private final TradeHistoryJpaRepository tradeHistoryJpaRepository;

	/**
	 * 거래 내역 저장
	 */
	public void save(final TradeHistory tradeHistory) {
		tradeHistoryJpaRepository.save(tradeHistory);
	}

	/**
	 * 모든 고유 회사 코드 조회
	 */
	public List<String> findDistinctCompanyCodes() {
		return tradeHistoryJpaRepository.findDistinctCompanyCodes();
	}

	/**
	 * 특정 회사의 최근 N개 거래 내역 조회
	 */
	public List<TradeHistory> findRecentTradesByCompanyCode(String companyCode, int limit) {
		return tradeHistoryJpaRepository.findRecentTradesByCompanyCode(companyCode, PageRequest.of(0, limit));
	}

	public List<TradeHistory> getTradeHistory() {
		return tradeHistoryJpaRepository.findAll();
	}

}
