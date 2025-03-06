package org.scoula.backend.member.controller;

import java.util.List;

import org.scoula.backend.member.dto.CompanySearchResponseDto;
import org.scoula.backend.member.service.CompanyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/companies")
@Slf4j
public class CompanyController {

	private final CompanyService companyService;

	public CompanyController(final CompanyService companyService) {
		this.companyService = companyService;
	}

	/**
	 * 검색어를 기반으로 회사를 조회하는 메서드
	 *
	 * @param query 검색어 (회사명 또는 코드)
	 * @return 검색된 회사 리스트
	 */
	@GetMapping("/search")
	public List<CompanySearchResponseDto> searchCompanies(@RequestParam final String query) {
		List<CompanySearchResponseDto> companies = companyService.searchCompanies(query);
		return companies;
	}
}
