package org.scoula.backend.order.repository;

import org.scoula.backend.order.domain.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeHistoryJpaRepository extends JpaRepository<TradeHistory, Long> {
}
