package org.scoula.backend.member.controller.response;

import org.scoula.backend.member.domain.Company;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 회사 검색 결과를 담는 DTO
 */
@Getter
@Builder(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class CompanySearchResponseDto {

	private final String isuNm;      // 한글 종목명
	private final String isuSrtCd;   // 단축코드
	private final String mktTpNm;    // 시장구분
	private final String secugrpNm;  // 증권구분
	private final String isuAbbrv; // 한글 약어

	/**
	 * Company 엔티티를 CompanySearchResponseDto로 변환하는 메서드
	 *
	 * @param company 변환할 Company 엔티티
	 * @return 변환된 CompanySearchResponseDto 객체
	 */
	public static CompanySearchResponseDto fromEntity(final Company company) {
		return CompanySearchResponseDto.builder()
			.isuNm(company.getIsuNm())
			.isuSrtCd(company.getIsuSrtCd())
			.mktTpNm(company.getMktTpNm())
			.secugrpNm(company.getSecugrpNm())
			.isuAbbrv(company.getIsuAbbrv())
			.build();
	}
}
