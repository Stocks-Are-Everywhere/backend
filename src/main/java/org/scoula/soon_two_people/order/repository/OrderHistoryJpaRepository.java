package org.scoula.soon_two_people.order.repository;

import org.scoula.soon_two_people.order.domain.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryJpaRepository extends JpaRepository<OrderHistory, Long> {

}
