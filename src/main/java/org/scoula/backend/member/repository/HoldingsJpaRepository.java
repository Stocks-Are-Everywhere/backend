package org.scoula.backend.member.repository;

import java.util.Optional;

import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingsJpaRepository extends JpaRepository<Holdings, Long> {
	Optional<Holdings> findByAccountIdAndCompanyCode(final Long accountId, final String companyCode);
}
