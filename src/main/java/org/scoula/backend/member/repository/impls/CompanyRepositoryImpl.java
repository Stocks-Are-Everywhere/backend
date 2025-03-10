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
	 * 이름 또는 단축코드를 포함하는 회사 목록을 조회하는 메서드
	 *
	 * @param query 검색어
	 * @return 검색된 회사 리스트
	 */
	public List<Company> findByIsuNmContainingOrIsuAbbrvContainingOrIsuEngNmContainingOrIsuSrtCdContaining(
		final String query) {
		return companyJpaRepository.findByIsuNmContainingOrIsuAbbrvContainingOrIsuEngNmContainingOrIsuSrtCdContaining(
			query,
			query, query, query);
	}

	public List<Company> findAll() {
		return companyJpaRepository.findAll();
	}

	public void save(Company company) {
		//현재는 사용 X
	}

	public Optional<Company> findByIsuSrtCd(final String isuSrt) {
		return companyJpaRepository.findByIsuSrtCd(isuSrt);
	}
}
