package org.scoula.backend.member.repository.impls;

import java.util.Optional;

import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.exception.MemberNotFoundException;
import org.scoula.backend.member.repository.MemberJpaRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

	private final MemberJpaRepository memberJpaRepository;

	// Get: 존재하지 않으면 예외 발생
	// Find: 존재하지 않으면 Optional.empty() 반환
	@Override
	public Member getByUsername(final String username) {
		return memberJpaRepository.findByUsername(username)
				.orElseThrow(MemberNotFoundException::new);
	}

	@Override
	public Member getByEmail(final String email) {
		return memberJpaRepository.findByEmail(email)
				.orElseThrow(MemberNotFoundException::new);
	}

	@Override
	public Optional<Member> findByEmail(final String email) {
		return memberJpaRepository.findByEmail(email);
	}

	@Override
	public Member save(final Member member) {
		return memberJpaRepository.save(member);
	}
}
