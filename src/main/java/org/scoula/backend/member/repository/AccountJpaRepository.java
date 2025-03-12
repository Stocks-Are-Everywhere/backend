package org.scoula.backend.member.repository;

import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    @Lock(value = LockModeType.OPTIMISTIC)
    Optional<Account> findByMemberId(Long memberId);

    @Lock(value = LockModeType.OPTIMISTIC)
    Optional<Account> findById(Long id);
}
