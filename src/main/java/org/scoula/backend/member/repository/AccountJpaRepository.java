package org.scoula.backend.member.repository;

import java.util.Optional;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

	@Lock(LockModeType.OPTIMISTIC)
	Optional<Account> findByMemberId(Long memberId);

	@Lock(LockModeType.OPTIMISTIC)
	Optional<Account> findByMember(Member member);
}
