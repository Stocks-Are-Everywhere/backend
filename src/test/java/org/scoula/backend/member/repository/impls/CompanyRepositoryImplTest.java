package org.scoula.backend.member.repository.impls;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Company;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CompanyRepositoryImpl.class)
class CompanyRepositoryImplTest {

	@Autowired
	private CompanyRepositoryImpl companyRepository;

	@Autowired
	private TestEntityManager entityManager;

	//company는 save X

	private Company createSampleCompany(String isuSrtCd, String isuNm) {
		return Company.builder()
			.isuCd("KR" + isuSrtCd)
			.isuSrtCd(isuSrtCd)
			.isuNm(isuNm)
			.isuAbbrv(isuNm)
			.isuEngNm(isuNm + " Co., Ltd.")
			.listDd("2023-01-01")
			.mktTpNm("KOSPI")
			.secugrpNm("주권")
			.kindStkcertTpNm("보통주")
			.parval("100")
			.listShrs("1000000")
			.build();
	}

	@BeforeEach
	void setUp() {
		entityManager.persist(createSampleCompany("005930", "삼성전자"));
		entityManager.persist(createSampleCompany("035420", "NAVER"));
		entityManager.persist(createSampleCompany("035720", "카카오"));
	}

	@Test
	@DisplayName("TC2-1-1: 전체 종목 목록 조회 테스트")
	void TC2_1_1_전체_종목_목록_조회() {
		List<Company> companies = companyRepository.findAll();
		assertThat(companies).hasSize(3);
		assertThat(companies).extracting(Company::getIsuNm)
			.containsExactlyInAnyOrder("삼성전자", "NAVER", "카카오");
	}

	@Test
	@DisplayName("TC2-1-3: 종목 검색 테스트")
	void TC2_1_3_종목_검색() {
		List<Company> results = companyRepository.findByIsuNmContainingOrIsuAbbrvContainingOrIsuEngNmContainingOrIsuSrtCdContaining(
			"삼성");
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getIsuNm()).isEqualTo("삼성전자");
	}

	@Test
	@DisplayName("TC2-3-1: 존재하지 않는 종목 검색 테스트")
	void TC2_3_1_존재하지_않는_종목_검색() {
		List<Company> results = companyRepository.findByIsuNmContainingOrIsuAbbrvContainingOrIsuEngNmContainingOrIsuSrtCdContaining(
			"존재하지않는회사");
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("TC2-4-1: 대량 종목 데이터 로딩 테스트")
	void TC2_4_1_대량_종목_데이터_로딩() {
		// 기존 데이터 삭제
		entityManager.clear();
		companyRepository.findAll().forEach(company -> entityManager.remove(company));

		// 1000개의 회사 데이터 생성 및 저장
		List<Company> companies = IntStream.range(0, 1000)
			.mapToObj(i -> createSampleCompany(String.format("%06d", i), "회사" + i))
			.collect(Collectors.toList());
		companies.forEach(entityManager::persist);
		entityManager.flush();

		List<Company> loadedCompanies = companyRepository.findAll();
		assertThat(loadedCompanies).hasSize(1000);
	}

	@Test
	@DisplayName("TC20-1-2: 데이터 정합성 검증 테스트")
	void TC20_1_2_데이터_정합성_검증() {
		Optional<Company> company = companyRepository.findByIsuSrtCd("005930");
		assertThat(company).isPresent();
		assertThat(company.get().getIsuNm()).isEqualTo("삼성전자");
		assertThat(company.get().getIsuCd()).isEqualTo("KR005930");
	}

}


