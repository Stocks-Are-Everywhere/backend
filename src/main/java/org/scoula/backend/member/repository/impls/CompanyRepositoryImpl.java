package org.scoula.backend.member.repository.impls;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.CompanyJpaRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CompanyRepositoryImpl implements CompanyRepository {

	private final CompanyJpaRepository companyJpaRepository;

	/**
	 * 이름 또는 단축코드를 포함하는 회사 목록을 조회하는 메서드
	 *
	 * @param query 검색어
	 * @return 검색된 회사 리스트
	 */
	@Override
	public List<Company> findByIsuNmContainingOrIsuSrtCdContaining(final String query) {
		List<Company> companies = companyJpaRepository.findByIsuNmContainingOrIsuAbbrvContaining(query, query);
		return companies;
	}

	@Override
	public List<Company> findAll() {
		return companyJpaRepository.findAll();
	}

	@Override
	public void save(Company company) {
		//현재는 사용 X
	}

	@Override
	public Optional<Company> findByIsuSrtCd(final String isuSrt) {
		return companyJpaRepository.findByIsuSrtCd(isuSrt);
	}
}
