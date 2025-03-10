package org.scoula.backend.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.dto.ranking.ListedSharesRankingDto;
import org.scoula.backend.order.dto.ranking.TurnoverRateRankingDto;
import org.scoula.backend.order.dto.ranking.VolumeRankingDto;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

	@Mock
	private OrderService orderService;

	@Mock
	private CompanyRepositoryImpl companyRepository;

	@InjectMocks
	private RankingService rankingService;

	@BeforeEach
	void setUp() {
		Map<String, OrderSummaryResponse> mockSummaries = new HashMap<>();
		mockSummaries.put("A001", new OrderSummaryResponse("A001", 100, 200));
		mockSummaries.put("B001", new OrderSummaryResponse("B001", 150, 250));

		Company companyA = Company.builder()
			.isuSrtCd("A001")
			.isuNm("회사A")
			.listShrs("1000000")
			.build();

		Company companyB = Company.builder()
			.isuSrtCd("B001")
			.isuNm("회사B")
			.listShrs("2000000")
			.build();

		given(orderService.getAllOrderSummaries()).willReturn(mockSummaries);
		given(companyRepository.findByIsuSrtCd("A001")).willReturn(Optional.of(companyA));
		given(companyRepository.findByIsuSrtCd("B001")).willReturn(Optional.of(companyB));
	}

	@Test
	@DisplayName("거래량 랭킹 테스트")
	void testGetVolumeRankings() {
		List<VolumeRankingDto> result = rankingService.getVolumeRankings();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getCompanyCode()).isEqualTo("B001");
		assertThat(result.get(0).getTotalVolume()).isEqualTo(400);
		assertThat(result.get(0).getRank()).isEqualTo(1);
		assertThat(result.get(1).getCompanyCode()).isEqualTo("A001");
		assertThat(result.get(1).getTotalVolume()).isEqualTo(300);
		assertThat(result.get(1).getRank()).isEqualTo(2);
	}

	@Test
	@DisplayName("상장주식수 랭킹 테스트")
	void testGetListedSharesRankings() {
		List<ListedSharesRankingDto> result = rankingService.getListedSharesRankings();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getCompanyCode()).isEqualTo("B001");
		assertThat(result.get(0).getListedShares()).isEqualTo(2000000);
		assertThat(result.get(0).getRank()).isEqualTo(1);
		assertThat(result.get(1).getCompanyCode()).isEqualTo("A001");
		assertThat(result.get(1).getListedShares()).isEqualTo(1000000);
		assertThat(result.get(1).getRank()).isEqualTo(2);
	}

	@Test
	@DisplayName("거래회전율 랭킹 테스트")
	void testGetTurnoverRateRankings() {
		List<TurnoverRateRankingDto> result = rankingService.getTurnoverRateRankings();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getCompanyCode()).isEqualTo("A001");
		assertThat(result.get(0).getTurnoverRate()).isEqualTo(0.0003);
		assertThat(result.get(0).getRank()).isEqualTo(1);
		assertThat(result.get(1).getCompanyCode()).isEqualTo("B001");
		assertThat(result.get(1).getTurnoverRate()).isEqualTo(0.0002);
		assertThat(result.get(1).getRank()).isEqualTo(2);
	}
}


