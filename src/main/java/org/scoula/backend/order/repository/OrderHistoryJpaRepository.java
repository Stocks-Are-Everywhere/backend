package org.scoula.backend.order.repository;

import org.scoula.backend.order.domain.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryJpaRepository extends JpaRepository<OrderHistory, Long> {

}
