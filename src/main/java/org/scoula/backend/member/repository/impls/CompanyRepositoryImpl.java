package org.scoula.backend.member.repository.impls;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.CompanyJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CompanyRepositoryImpl {

	private final CompanyJpaRepository companyJpaRepository;

	/**
	 * 단축코드로 회사를 조회하는 메서드
	 *
	 * @param tickerCode 단축코드
	 * @return 조회된 회사 객체
	 */
	public Optional<Company> findByIsuSrtCd(final String tickerCode) {
		return companyJpaRepository.findByIsuSrtCd(tickerCode);
	}

	/**
	 * 이름 또는 단축코드를 포함하는 회사 목록을 조회하는 메서드
	 *
	 * @param query 검색어
	 * @return 검색된 회사 리스트
	 */
	public List<Company> findByIsuNmContainingOrIsuSrtCdContaining(final String query) {
		List<Company> companies = companyJpaRepository.findByIsuNmContainingOrIsuAbbrvContaining(query, query);
		return companies;
	}
}
