package org.scoula.backend.member.dto;

import org.scoula.backend.member.domain.Company;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyDto {

	private final String isuCd;        // 표준코드
	private final String isuSrtCd;     // 단축코드
	private final String isuNm;        // 한글 종목명
	private final String isuAbbrv;     // 한글 종목약명
	private final String isuEngNm;     // 영문 종목명
	private final String listDd;       // 상장일
	private final String mktTpNm;      // 시장구분
	private final String secugrpNm;    // 증권구분
	private final String sectTpNm;     // 소속부 (nullable)
	private final String kindStkcertTpNm; // 주식 종류
	private final String parval;       // 액면가
	private final String listShrs;     // 상장 주식 수

	/**
	 * Entity -> DTO 변환 메서드
	 *
	 * @param company Company 엔티티
	 * @return 변환된 CompanyDto 객체
	 */
	public static CompanyDto fromEntity(final Company company) {
		return new CompanyDto(
			company.getIsuCd(),
			company.getIsuSrtCd(),
			company.getIsuNm(),
			company.getIsuAbbrv(),
			company.getIsuEngNm(),
			company.getListDd(),
			company.getMktTpNm(),
			company.getSecugrpNm(),
			company.getSectTpNm(),
			company.getKindStkcertTpNm(),
			company.getParval(),
			company.getListShrs()
		);
	}

}
