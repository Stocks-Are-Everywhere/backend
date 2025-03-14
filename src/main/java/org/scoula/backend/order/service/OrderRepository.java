package org.scoula.backend.order.service;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.order.domain.Order;

public interface OrderRepository {

	Order save(final Order order);

	Optional<Order> findById(final Long id);

	Boolean existsById(final Long id);

	List<Order> findByCompanyCode(final String number);

	List<Order> findAll();

	List<Order> saveAll(final List<Order> orders);
}
