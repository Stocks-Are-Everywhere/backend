package org.scoula.backend.order.controller;

import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.service.TradeHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

	@GetMapping("/{symbol}/history")
	@Operation(summary = "차트 히스토리 조회", description = "특정 종목의 차트 데이터를 조회합니다.")
	public ResponseEntity<ChartResponseDto> getChartHistory(
			@PathVariable("symbol")
			@Parameter(description = "종목 코드", required = true)
			String symbol,

			@RequestParam(value = "timeFrame", defaultValue = "15m")
			@Parameter(description = "타임프레임 (15s, 1m, 5m, 15m, 30m, 1h)", example = "15m")
			String timeFrame) {

		try {
			// 입력값 로깅
			log.debug("차트 히스토리 요청: 종목={}, 타임프레임={}", symbol, timeFrame);

			// 데이터 조회
			ChartResponseDto chartData = tradeHistoryService.getChartHistory(symbol, timeFrame);

			// 정상적인 응답인지 확인
			if (chartData == null || chartData.candles() == null) {
				log.warn("차트 데이터가 null입니다: 종목={}, 타임프레임={}", symbol, timeFrame);
				return ResponseEntity.noContent().build();
			}

			// 캔들 데이터 유효성 확인
			boolean hasInvalidCandles = chartData.candles().stream()
					.anyMatch(candle -> candle == null || candle.time() == null || candle.time() <= 0);

			if (hasInvalidCandles) {
				log.warn("유효하지 않은 캔들이 포함되어 있습니다: 종목={}, 타임프레임={}", symbol, timeFrame);

				// 유효하지 않은 캔들 필터링 (추가 방어 조치)
				ChartResponseDto filteredData = ChartResponseDto.builder()
						.candles(chartData.candles().stream()
								.filter(candle -> candle != null && candle.time() != null && candle.time() > 0)
								.toList())
						.timeCode(chartData.timeCode())
						.build();

				return ResponseEntity.ok(filteredData);
			}

			// 정상 응답
			log.debug("차트 데이터 응답: 종목={}, 타임프레임={}, 캔들 수={}",
					symbol, timeFrame, chartData.candles().size());
			return ResponseEntity.ok(chartData);
		} catch (Exception e) {
			log.error("차트 히스토리 조회 중 오류 발생: 종목={}, 타임프레임={}, 오류={}",
					symbol, timeFrame, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
}
