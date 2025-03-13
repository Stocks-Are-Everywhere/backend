package org.scoula.backend.member.repository;

import java.util.Optional;

import org.scoula.backend.member.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

	@Lock(value = LockModeType.PESSIMISTIC_READ)
	@Transactional
	Optional<Account> findByMemberId(Long memberId);

	@Lock(value = LockModeType.PESSIMISTIC_READ)
	@Transactional
	Optional<Account> findById(Long id);
}
