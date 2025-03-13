package org.scoula.backend.member.service.reposiotry;

import org.scoula.backend.member.domain.Holdings;

import java.util.Optional;

public interface HoldingsRepository {

    Holdings save(final Holdings holdings);

    Optional<Holdings> findByAccountIdAndCompanyCode(final Long accountId, final String companyCode);
}
