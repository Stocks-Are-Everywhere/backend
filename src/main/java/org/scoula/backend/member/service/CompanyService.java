package org.scoula.backend.member.service;

import java.util.List;
import java.util.stream.Collectors;

import org.scoula.backend.member.dto.CompanySearchResponseDto;
import org.scoula.backend.member.repository.CompanyJpaRepository;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CompanyService {

	private final CompanyRepositoryImpl companyRepositoryImpl;
	private final CompanyJpaRepository companyJpaRepository;

	public CompanyService(final CompanyRepositoryImpl companyRepositoryImpl,
		final CompanyJpaRepository companyJpaRepository) {
		this.companyRepositoryImpl = companyRepositoryImpl;
		this.companyJpaRepository = companyJpaRepository;
	}

	/**
	 * 검색어를 포함하는 회사 목록을 조회하는 메서드
	 *
	 * @param query 검색어
	 * @return 검색된 회사 리스트
	 */
	public List<CompanySearchResponseDto> searchCompanies(final String query) {
		return companyRepositoryImpl.findByIsuNmContainingOrIsuSrtCdContaining(query)
			.stream()
			.map(CompanySearchResponseDto::fromEntity) // 검색 전용 DTO로 변환
			.collect(Collectors.toList());
	}

}
