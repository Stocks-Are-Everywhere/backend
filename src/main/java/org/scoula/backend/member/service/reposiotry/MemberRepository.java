package org.scoula.backend.member.service.reposiotry;

import lombok.RequiredArgsConstructor;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.exception.MemberNotFoundException;
import org.scoula.backend.member.repository.MemberJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface MemberRepository {

	Member getByUsername(final String username);

	Member getByEmail(final String email);

	Optional<Member> findByEmail(final String email);

	Member save(final Member member);
}
