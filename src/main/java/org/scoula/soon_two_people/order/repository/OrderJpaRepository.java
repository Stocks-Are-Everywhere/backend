package org.scoula.soon_two_people.order.repository;

import org.scoula.soon_two_people.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

}
