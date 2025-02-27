package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.repository.MemberJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl {

	private final MemberJpaRepository memberJpaRepository;

}
