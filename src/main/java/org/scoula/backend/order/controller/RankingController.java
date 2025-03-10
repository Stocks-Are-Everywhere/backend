package org.scoula.backend.order.controller;

import java.util.List;

import org.scoula.backend.order.dto.ranking.ListedSharesRankingDto;
import org.scoula.backend.order.dto.ranking.TurnoverRateRankingDto;
import org.scoula.backend.order.dto.ranking.VolumeRankingDto;
import org.scoula.backend.order.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

	private final RankingService rankingService;

	@GetMapping("/volume")
	public ResponseEntity<List<VolumeRankingDto>> getVolumeRankings() {
		return ResponseEntity.ok(rankingService.getVolumeRankings());
	}

	@GetMapping("/listedshares")
	public ResponseEntity<List<ListedSharesRankingDto>> getListedSharesRankings() {
		return ResponseEntity.ok(rankingService.getListedSharesRankings());
	}

	@GetMapping("/turnoverrate")
	public ResponseEntity<List<TurnoverRateRankingDto>> getTurnoverRateRankings() {
		return ResponseEntity.ok(rankingService.getTurnoverRateRankings());
	}
}
