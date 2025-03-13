package org.scoula.backend.order.repository;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.service.OrderRepository;
import org.scoula.backend.order.service.exception.OrderNotFoundExeception;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

	private final OrderJpaRepository orderJpaRepository;

	@Override
	public Order save(final Order order) {
		return orderJpaRepository.save(order);
	}

	@Override
	public Order getById(final Long id) {
		return orderJpaRepository.findById(id)
				.orElseThrow(OrderNotFoundExeception::new);
	}

	@Override
	public List<Order> findByCompanyCode(final String number) {
		return null;
	}
}
