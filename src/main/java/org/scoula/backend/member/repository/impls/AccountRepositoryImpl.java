package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.exception.AccountNotFoundException;
import org.scoula.backend.member.repository.AccountJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl {

	private final AccountJpaRepository accountJpaRepository;

	public Account getByMemberId(final Long memberId) {
		return accountJpaRepository.findByMemberId(memberId)
				.orElseThrow(AccountNotFoundException::new);
	}

	public Account getById(final Long id) {
		return accountJpaRepository.findById(id)
				.orElseThrow(AccountNotFoundException::new);
	}

	public Account save(final Account account) {
		return accountJpaRepository.save(account);
	}
}
