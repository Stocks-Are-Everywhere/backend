package org.scoula.backend.order.repository;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl {

	private final OrderJpaRepository orderJpaRepository;

}
