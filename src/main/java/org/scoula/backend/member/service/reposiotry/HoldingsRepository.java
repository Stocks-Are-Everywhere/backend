package org.scoula.backend.member.service.reposiotry;

import java.util.Optional;

import org.scoula.backend.member.domain.Holdings;

public interface HoldingsRepository {
	Optional<Holdings> findByAccountIdAndCompanyCode(final Long accountId, final String companyCode);

	Holdings save(final Holdings holdings);
}
