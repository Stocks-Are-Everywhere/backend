package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

}
