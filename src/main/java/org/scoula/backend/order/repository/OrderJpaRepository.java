package org.scoula.backend.order.repository;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
	Optional<List<Order>> findByTypeAndCompanyCodeAndAccountIdAndStatus(Type type, String companyCode, Long accountId, OrderStatus status);
}
