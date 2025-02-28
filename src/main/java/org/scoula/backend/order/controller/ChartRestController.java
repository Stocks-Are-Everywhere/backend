package org.scoula.backend.order.controller;

import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.service.TradeHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/chart")
@Tag(name = "차트 초기 데이터 API", description = "차트 생성시 초기 데이터를 보내주는 컨트롤러 입니다.")
@RequiredArgsConstructor
@Slf4j
public class ChartRestController {

	private final TradeHistoryService tradeHistoryService;

	@Operation(summary = "차트 초기 데이터 조회")
	@GetMapping("/{symbol}/history")
	public ResponseEntity<ChartResponseDto> getChartHistory(@PathVariable("symbol") String symbol) {
		try {
			ChartResponseDto response = tradeHistoryService.getChartHistory(symbol);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Failed to get chart history for symbol: {}", symbol, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}
}
