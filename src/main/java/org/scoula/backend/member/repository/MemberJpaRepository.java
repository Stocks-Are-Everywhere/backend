package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);
    Optional<Member> findByEmail(String email);
    Optional<Member> findByGoogleId(String googleId);
    
    @Query("SELECT m FROM Member m JOIN FETCH m.account WHERE m.username = :username")
    Optional<Member> findByUsernameWithAccount(String username);
}
