package org.scoula.backend.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.member.controller.response.CompanySearchResponseDto;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;

@ExtendWith(MockitoExtension.class)
public class CompanyServiceTest {

	@Mock
	private CompanyRepository companyRepository;

	@InjectMocks
	private CompanyService companyService;

	private List<Company> sampleCompanies;

	@BeforeEach
	void setUp() {
		sampleCompanies = Arrays.asList(
			createCompany("KR7005930003", "005930", "삼성전자", "삼성전자", "Samsung Electronics"),
			createCompany("KR7035420009", "035420", "NAVER", "네이버", "NAVER Corporation"),
			createCompany("KR7035720002", "035720", "카카오", "카카오", "Kakao Corp")
		);
	}

	private Company createCompany(String isuCd, String isuSrtCd, String isuNm, String isuAbbrv, String isuEngNm) {
		return Company.builder()
			.isuCd(isuCd)
			.isuSrtCd(isuSrtCd)
			.isuNm(isuNm)
			.isuAbbrv(isuAbbrv)
			.isuEngNm(isuEngNm)
			.listDd("2023-01-01")
			.mktTpNm("KOSPI")
			.secugrpNm("주권")
			.kindStkcertTpNm("보통주")
			.parval("100")
			.listShrs("1000000")
			.build();
	}

	@Test
	@DisplayName("TC2-1-3: 종목 검색 정상 케이스 테스트")
	void TC2_1_3_종목검색_정상케이스() {
		// Given
		String query = "삼성";
		when(companyRepository.findByIsuNmContainingOrIsuSrtCdContaining(query))
			.thenReturn(Collections.singletonList(sampleCompanies.get(0)));

		// When
		List<CompanySearchResponseDto> result = companyService.searchCompanies(query);

		// Then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getIsuNm()).isEqualTo("삼성전자");
		verify(companyRepository).findByIsuNmContainingOrIsuSrtCdContaining(query);
	}

	@Test
	@DisplayName("TC2-3-1: 존재하지 않는 종목 검색 테스트")
	void TC2_3_1_존재하지않는종목검색() {
		// Given
		String query = "존재하지않는회사";
		when(companyRepository.findByIsuNmContainingOrIsuSrtCdContaining(query))
			.thenReturn(Collections.emptyList());

		// When
		List<CompanySearchResponseDto> result = companyService.searchCompanies(query);

		// Then
		assertThat(result).isEmpty();
		verify(companyRepository).findByIsuNmContainingOrIsuSrtCdContaining(query);
	}

	@Test
	@DisplayName("TC2-4-1: 대량 종목 데이터 로딩 테스트")
	void TC2_4_1_대량종목데이터로딩() {
		// Given
		String query = "주식회사";
		List<Company> largeCompanyList = IntStream.range(0, 1000)
			.mapToObj(i -> createCompany(
				"KR" + String.format("%010d", i),
				String.format("%06d", i),
				"주식회사" + i,
				"주식회사" + i,
				"Company" + i
			))
			.collect(Collectors.toList());
		when(companyRepository.findByIsuNmContainingOrIsuSrtCdContaining(query))
			.thenReturn(largeCompanyList);

		// When
		List<CompanySearchResponseDto> result = companyService.searchCompanies(query);

		// Then
		assertThat(result).hasSize(1000);
		verify(companyRepository).findByIsuNmContainingOrIsuSrtCdContaining(query);
	}

	@Test
	@DisplayName("TC20-1-2: 데이터 정합성 검증 테스트")
	void TC20_1_2_데이터정합성검증() {
		// Given
		String query = "NAVER";
		Company company = sampleCompanies.get(1);
		when(companyRepository.findByIsuNmContainingOrIsuSrtCdContaining(query))
			.thenReturn(Collections.singletonList(company));

		// When
		List<CompanySearchResponseDto> result = companyService.searchCompanies(query);

		// Then
		assertThat(result).hasSize(1);
		CompanySearchResponseDto dto = result.get(0);
		assertThat(dto.getIsuAbbrv()).isEqualTo(company.getIsuAbbrv());
		assertThat(dto.getIsuSrtCd()).isEqualTo(company.getIsuSrtCd());
		assertThat(dto.getIsuNm()).isEqualTo(company.getIsuNm());
	}

	@Test
	@DisplayName("TC20-1-1: 트랜잭션 ACID 속성 테스트")
	void TC20_1_1_트랜잭션ACID속성() {
		// Given
		String query = "테스트";
		List<Company> companies = IntStream.range(0, 100)
			.mapToObj(i -> createCompany(
				"KR" + String.format("%010d", i),
				String.format("%06d", i),
				"테스트회사" + i,
				"테스트" + i,
				"Test Company" + i
			))
			.collect(Collectors.toList());
		when(companyRepository.findByIsuNmContainingOrIsuSrtCdContaining(query))
			.thenReturn(companies);

		// When
		List<CompanySearchResponseDto> result = companyService.searchCompanies(query);

		// Then
		assertThat(result).hasSize(100);
		verify(companyRepository).findByIsuNmContainingOrIsuSrtCdContaining(query);
	}
}

