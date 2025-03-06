package org.scoula.backend.member.controller.request;

import jakarta.validation.constraints.Size;

public class CompanySearchRequestDto {
	@Size(min = 1, message = "검색어는 최소 2자 이상이어야 합니다.")
	private final String keyword;

	public CompanySearchRequestDto(String keyword) {
		this.keyword = keyword;
	}
}

