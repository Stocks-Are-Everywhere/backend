package org.scoula.backend.member.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration
class CompanyTest {

	private final Company company = Company.builder()
		.isuCd("005930")
		.closingPrice(new BigDecimal(1000))
		.build();

	@Test
	@DisplayName("회사 엔티티 생성 및 조회 테스트")
	void testCompanyCreationAndRetrieval() {
		Company company = createSampleCompany();;

		assertThat(company).isNotNull();
		assertThat(company.getIsuSrtCd()).isEqualTo("005930");
		assertThat(company.getIsuNm()).isEqualTo("삼성전자");
	}

	@Test
	@DisplayName("회사 엔티티 동등성 비교 테스트")
	void testCompanyEquality() {
		Company company1 = createSampleCompany();
		Company company2 = createSampleCompany();

		assertThat(company1)
			.usingRecursiveComparison()
			.ignoringFields("id")
			.isEqualTo(company2);
	}

	private Company createSampleCompany() {
		return Company.builder()
			.isuCd("KR7005930003")
			.isuSrtCd("005930")
			.isuNm("삼성전자")
			.isuAbbrv("삼성전자")
			.isuEngNm("Samsung Electronics Co., Ltd.")
			.listDd("1975-06-11")
			.mktTpNm("KOSPI")
			.secugrpNm("주권")
			.kindStkcertTpNm("보통주")
			.parval("100")
			.listShrs("5969782550")
			.build();
	}

	@Test
	@DisplayName("회사 엔티티 빌더 패턴 테스트")
	void testBuilderPattern() {
		Company company = Company.builder()
			.isuCd("KR7005930003")
			.isuSrtCd("005930")
			.isuNm("삼성전자")
			.build();

		assertThat(company.getIsuCd()).isEqualTo("KR7005930003");
		assertThat(company.getIsuSrtCd()).isEqualTo("005930");
		assertThat(company.getIsuNm()).isEqualTo("삼성전자");
	}

	@Test
	@DisplayName("회사 엔티티 빌더 toString 메서드 테스트")
	void testBuilderToString() {
		String builderString = Company.builder()
			.isuCd("KR7005930003")
			.isuSrtCd("005930")
			.isuNm("삼성전자")
			.toString();
		assertThat(builderString).contains("isuCd", "isuSrtCd", "isuNm");
	}

	@Test
	@DisplayName("입력된 가격이 종가 기준 상향 30% 이상일 경우 true를 반환한다.")
	void returnTrueWhenPriceWithinUpperLimit() {
		// given
		BigDecimal price = new BigDecimal(1300);

		// when
		boolean isWithinRange = company.isWithinClosingPriceRange(price);

		// then
		assertThat(isWithinRange).isTrue();
	}

	@Test
	@DisplayName("입력된 가격이 종가 기준 상향 30%를 초과할 경우 false를 반환한다.")
	void returnFalseWhenPriceExceedsUpperLimit() {
		// given
		BigDecimal price = new BigDecimal(1301);

		// when
		boolean isWithinRange = company.isWithinClosingPriceRange(price);

		// then
		assertThat(isWithinRange).isFalse();
	}

	@Test
	@DisplayName("입력된 가격이 종가 기준 하향 30% 이하일 경우 true를 반환한다.")
	void returnTrueWhenPriceWithinLowerLimit() {
		// given
		BigDecimal price = new BigDecimal(700);

		// when
		boolean isWithinRange = company.isWithinClosingPriceRange(price);

		// then
		assertThat(isWithinRange).isTrue();
	}

	@Test
	@DisplayName("입력된 가격이 종가 기준 하향 30% 미만일 경우 false를 반환한다.")
	void returnFalseWhenPriceIsBelowLowerLimit() {
		// given
		BigDecimal price = new BigDecimal(699);

		// when
		boolean isWithinRange = company.isWithinClosingPriceRange(price);

		// then
		assertThat(isWithinRange).isFalse();
	}
}
