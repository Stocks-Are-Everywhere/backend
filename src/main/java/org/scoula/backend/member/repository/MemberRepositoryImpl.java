package org.scoula.backend.member.repository;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl {

	private final MemberJpaRepository memberJpaRepository;

}
