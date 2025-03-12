package org.scoula.backend.member.service.reposiotry;

import org.scoula.backend.member.domain.Account;

public interface AccountRepository {

	Account getByMemberId(final Long memberId);

	Account getById(final Long id);

	Account save(final Account account);
}
