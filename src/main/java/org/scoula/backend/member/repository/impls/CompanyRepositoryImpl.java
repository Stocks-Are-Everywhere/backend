package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.CompanyJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl {

	private final CompanyJpaRepository companyJpaRepository;

	public Optional<Company> findByIsuSrtCd(String tickerCode) {
		return companyJpaRepository.findByIsuSrtCd(tickerCode);
	}

}
