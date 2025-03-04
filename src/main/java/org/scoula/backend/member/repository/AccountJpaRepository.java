package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByMemberId(Long memberId);
}
