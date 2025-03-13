package org.scoula.backend.member.service.reposiotry;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.member.domain.Company;

public interface CompanyRepository {

	/**
	 * 이름 또는 단축코드를 포함하는 회사 목록을 조회하는 메서드
	 *
	 * @param query 검색어
	 * @return 검색된 회사 리스트
	 */
	List<Company> findByIsuNmContainingOrIsuAbbrvContainingOrIsuEngNmContainingOrIsuSrtCdContaining(final String query);

	List<Company> findAll();

	void save(final Company company);

	Optional<Company> findByIsuSrtCd(final String isuSrt);
}
