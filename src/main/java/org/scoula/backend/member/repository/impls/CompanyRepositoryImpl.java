package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.repository.CompanyJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl {

	private final CompanyJpaRepository companyJpaRepository;

}
