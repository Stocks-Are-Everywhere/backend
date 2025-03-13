package org.scoula.backend.member.service.reposiotry;

import org.scoula.backend.member.domain.Account;

public interface AccountRepository {

	Account getByMemberId(final Long memberId);

	Account save(final Account account);
}
