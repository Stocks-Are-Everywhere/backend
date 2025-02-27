package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.repository.WishJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WishRepositoryImpl {

	private final WishJpaRepository wishJpaRepository;

}
