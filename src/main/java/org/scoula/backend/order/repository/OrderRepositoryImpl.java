package org.scoula.backend.order.repository;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.order.domain.Order;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl {

	private final OrderJpaRepository orderJpaRepository;

	public Order save(Order order) {
		return orderJpaRepository.save(order);
	}

	public Optional<Order> findById(Long id) {
		return orderJpaRepository.findById(id);
	}

	public List<Order> findByCompanyCode(String number) {
		return null;
	}
}
