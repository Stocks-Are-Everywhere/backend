package org.scoula.soon_two_people.member.repository;

import org.scoula.soon_two_people.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

}
