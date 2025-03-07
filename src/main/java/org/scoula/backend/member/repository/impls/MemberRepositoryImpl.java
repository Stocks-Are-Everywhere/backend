package org.scoula.backend.member.repository.impls;

import java.util.Optional;

import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.exception.MemberNotFoundException;
import org.scoula.backend.member.repository.MemberJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl {

	private final MemberJpaRepository memberJpaRepository;

	// Get: 존재하지 않으면 예외 발생
	// Find: 존재하지 않으면 Optional.empty() 반환

	public Member getByUsername(final String username) {
		return memberJpaRepository.findByUsername(username)
				.orElseThrow(MemberNotFoundException::new);
	}

	public Member getByEmail(final String email) {
		return memberJpaRepository.findByEmail(email)
				.orElseThrow(MemberNotFoundException::new);
	}

	public Optional<Member> findByEmail(final String email) {
		return memberJpaRepository.findByEmail(email);
	}

	public Member save(final Member member) {
		return memberJpaRepository.save(member);
	}
}
