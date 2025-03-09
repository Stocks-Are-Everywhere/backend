package org.scoula.backend.member.repository.impls;

import java.util.Optional;

import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.repository.HoldingsJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HoldingsRepositoryImpl {

	private final HoldingsJpaRepository holdingsJpaRepository;

	public Holdings save(final Holdings holdings) {
		return holdingsJpaRepository.save(holdings);
	}

	public Optional<Holdings> findByAccountIdAndCompanyCode(final Long accountId, final String companyCode) {
		return holdingsJpaRepository.findByAccountIdAndCompanyCode(accountId, companyCode);
	}
}
