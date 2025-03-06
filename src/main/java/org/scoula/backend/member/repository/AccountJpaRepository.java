package org.scoula.backend.member.repository;

import java.util.Optional;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByMemberId(Long memberId);
    Optional<Account> findByMember(Member member);
}
