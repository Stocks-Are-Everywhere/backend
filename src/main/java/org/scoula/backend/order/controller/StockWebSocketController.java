package org.scoula.backend.order.controller;

import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.service.TradeHistoryService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Tag(name = "차트 WebSocket API", description = "초기 데이터를 기반으로 15초 단위 새로운 캔들을 전달하는 컨드롤러 입니다.")
@Slf4j
public class StockWebSocketController {

	private final TradeHistoryService tradeHistoryService;
	private final SimpMessagingTemplate messagingTemplate;

	// @MessageMapping("/chart/{symbol}")
	// @SendTo("/topic/chart/{symbol}")
	// public ChartUpdateDto handleChartUpdate(@DestinationVariable String symbol) {
	// 	try {
	// 		Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade(symbol);
	// 		return lastTrade.map(trade -> ChartUpdateDto.builder()
	// 						.price(trade.getPrice().doubleValue())
	// 						.volume(trade.getQuantity().intValue())
	// 						.build())
	// 				.orElse(null);
	// 	} catch (Exception e) {
	// 		log.error("Failed to handle chart update for symbol: {}", symbol, e);
	// 		return null;
	// 	}
	// }

	@Scheduled(fixedRate = 15000) // 15초마다 새로운 캔들 생성
	public void sendCandleUpdates() {
		try {
			// 새로운 캔들 생성 및 기존 캔들 업데이트
			tradeHistoryService.updateCandles("005930");

			// 업데이트된 캔들 데이터 전송
			ChartResponseDto candleData = tradeHistoryService.getChartHistory("005930");
			messagingTemplate.convertAndSend("/topic/candle/" + "005930", candleData);

			log.debug("Sent candle update for symbol: {}, candles size: {}",
					"005930", candleData.getCandles().size());
		} catch (Exception e) {
			log.error("Failed to send candle update for symbol: {}", "005930", e);
		}
	}

	// @Scheduled(fixedRate = 1000) // 1초마다 실시간 가격 업데이트
	// public void sendPriceUpdates() {
	// 	try {
	// 		Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade("005930");
	// 		lastTrade.ifPresent(trade -> {
	// 			ChartUpdateDto updateDto = ChartUpdateDto.builder()
	// 					.price(trade.getPrice().doubleValue())
	// 					.volume(trade.getQuantity().intValue())
	// 					.build();
	// 			messagingTemplate.convertAndSend("/topic/chart/" + "005930", updateDto);
	// 		});
	// 	} catch (Exception e) {
	// 		log.error("Failed to send price update for symbol: {}", "005930", e);
	// 	}
	// }
}
