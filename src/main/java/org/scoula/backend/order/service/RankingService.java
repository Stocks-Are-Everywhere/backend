package org.scoula.backend.order.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.dto.ranking.ListedSharesRankingDto;
import org.scoula.backend.order.dto.ranking.TurnoverRateRankingDto;
import org.scoula.backend.order.dto.ranking.VolumeRankingDto;
import org.springframework.stereotype.Service;

@Service
public class RankingService {
	private final OrderService orderService;
	private final CompanyRepositoryImpl companyRepository;

	public RankingService(OrderService orderService, CompanyRepositoryImpl companyRepository) {
		this.orderService = orderService;
		this.companyRepository = companyRepository;
	}

	public List<VolumeRankingDto> getVolumeRankings() {
		Map<String, OrderSummaryResponse> summaries = orderService.getAllOrderSummaries();
		List<VolumeRankingDto> rankings = new ArrayList<>();

		for (Map.Entry<String, OrderSummaryResponse> entry : summaries.entrySet()) {
			String companyCode = entry.getKey();
			OrderSummaryResponse summary = entry.getValue();
			Company company = companyRepository.findByIsuSrtCd(companyCode)
				.orElseThrow(() -> new RuntimeException("Company not found: " + companyCode));

			int totalVolume = summary.getSellCount() + summary.getBuyCount();

			rankings.add(VolumeRankingDto.builder()
				.companyCode(companyCode)
				.companyName(company.getIsuNm())
				.totalVolume(totalVolume)
				.build());
		}

		// 거래량 기준으로 내림차순 정렬
		rankings.sort(Comparator.comparing(VolumeRankingDto::getTotalVolume).reversed());

		// 랭킹 부여 및 상위 20개만 선택
		return IntStream.range(0, Math.min(rankings.size(), 20))
			.mapToObj(i -> VolumeRankingDto.builder()
				.companyCode(rankings.get(i).getCompanyCode())
				.companyName(rankings.get(i).getCompanyName())
				.totalVolume(rankings.get(i).getTotalVolume())
				.rank(i + 1)
				.build())
			.collect(Collectors.toList());
	}

	public List<ListedSharesRankingDto> getListedSharesRankings() {
		Map<String, OrderSummaryResponse> summaries = orderService.getAllOrderSummaries();
		List<ListedSharesRankingDto> rankings = new ArrayList<>();

		for (Map.Entry<String, OrderSummaryResponse> entry : summaries.entrySet()) {
			String companyCode = entry.getKey();
			Company company = companyRepository.findByIsuSrtCd(companyCode)
				.orElseThrow(() -> new RuntimeException("Company not found: " + companyCode));

			int listedShares = Integer.parseInt(company.getListShrs());

			rankings.add(ListedSharesRankingDto.builder()
				.companyCode(companyCode)
				.companyName(company.getIsuNm())
				.listedShares(listedShares)
				.build());
		}

		// 상장주식수 기준으로 내림차순 정렬
		rankings.sort(Comparator.comparing(ListedSharesRankingDto::getListedShares).reversed());

		// 랭킹 부여 및 상위 20개만 선택
		return IntStream.range(0, Math.min(rankings.size(), 20))
			.mapToObj(i -> ListedSharesRankingDto.builder()
				.companyCode(rankings.get(i).getCompanyCode())
				.companyName(rankings.get(i).getCompanyName())
				.listedShares(rankings.get(i).getListedShares())
				.rank(i + 1)
				.build())
			.collect(Collectors.toList());
	}

	public List<TurnoverRateRankingDto> getTurnoverRateRankings() {
		Map<String, OrderSummaryResponse> summaries = orderService.getAllOrderSummaries();
		List<TurnoverRateRankingDto> rankings = new ArrayList<>();

		for (Map.Entry<String, OrderSummaryResponse> entry : summaries.entrySet()) {
			String companyCode = entry.getKey();
			OrderSummaryResponse summary = entry.getValue();
			Company company = companyRepository.findByIsuSrtCd(companyCode)
				.orElseThrow(() -> new RuntimeException("Company not found: " + companyCode));

			int totalVolume = summary.getSellCount() + summary.getBuyCount();
			int listedShares = Integer.parseInt(company.getListShrs());

			double turnoverRate = listedShares > 0 ? (double)totalVolume / listedShares : 0;

			rankings.add(TurnoverRateRankingDto.builder()
				.companyCode(companyCode)
				.companyName(company.getIsuNm())
				.turnoverRate(turnoverRate)
				.build());
		}

		// 거래회전율 기준으로 내림차순 정렬
		rankings.sort(Comparator.comparing(TurnoverRateRankingDto::getTurnoverRate).reversed());

		// 랭킹 부여 및 상위 20개만 선택
		return IntStream.range(0, Math.min(rankings.size(), 20))
			.mapToObj(i -> TurnoverRateRankingDto.builder()
				.companyCode(rankings.get(i).getCompanyCode())
				.companyName(rankings.get(i).getCompanyName())
				.turnoverRate(rankings.get(i).getTurnoverRate())
				.rank(i + 1)
				.build())
			.collect(Collectors.toList());
	}
}
