package org.scoula.soon_two_people.order.repository;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderHistoryRepositoryImpl {

	private final OrderHistoryJpaRepository orderHistoryJpaRepository;

}
