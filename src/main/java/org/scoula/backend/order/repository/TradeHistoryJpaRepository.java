package org.scoula.backend.order.repository;

import java.util.List;

import org.scoula.backend.order.domain.TradeHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeHistoryJpaRepository extends JpaRepository<TradeHistory, Long> {

	/**
	 * 모든 고유 회사 코드 조회
	 */
	@Query("SELECT DISTINCT t.companyCode FROM TradeHistory t")
	List<String> findDistinctCompanyCodes();

	/**
	 * 특정 회사의 최근 거래 내역 조회
	 */
	@Query("SELECT t FROM TradeHistory t WHERE t.companyCode = :companyCode ORDER BY t.tradeTime DESC")
	List<TradeHistory> findRecentTradesByCompanyCode(@Param("companyCode") String companyCode, Pageable pageable);

}
