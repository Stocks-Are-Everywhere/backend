package org.scoula.backend.order.controller;

import org.scoula.backend.order.domain.TimeFrame;
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
@Tag(name = "차트 WebSocket API", description = "초기 데이터를 기반으로 각 타임프레임별 새로운 캔들을 전달하는 컨드롤러 입니다.")
@Slf4j
public class ChartScheduler {

	private final TradeHistoryService tradeHistoryService;
	private final SimpMessagingTemplate messagingTemplate;

	// 기본 종목 코드
	private static final String DEFAULT_COMPANY_CODE = "005930";

	@Scheduled(fixedRate = 15000) // 15초마다 새로운 캔들 생성
	public void sendCandleUpdates15Sec() {
		sendCandleUpdates(DEFAULT_COMPANY_CODE, TimeFrame.SECONDS_15.getTimeCode());
	}

	// 1분봉 업데이트 (60초마다)
	@Scheduled(fixedRate = 60000)
	public void sendCandleUpdates1Min() {
		sendCandleUpdates(DEFAULT_COMPANY_CODE, TimeFrame.MINUTE_1.getTimeCode());
	}

	// 5분봉 업데이트 (5분마다)
	@Scheduled(fixedRate = 300000)
	public void sendCandleUpdates5Min() {
		sendCandleUpdates(DEFAULT_COMPANY_CODE, TimeFrame.MINUTE_5.getTimeCode());
	}

	// 15분봉 업데이트 (15분마다)
	@Scheduled(fixedRate = 900000)
	public void sendCandleUpdates15Min() {
		sendCandleUpdates(DEFAULT_COMPANY_CODE, TimeFrame.MINUTE_15.getTimeCode());
	}

	// 30분봉 업데이트 (30분마다)
	@Scheduled(fixedRate = 1800000)
	public void sendCandleUpdates30Min() {
		sendCandleUpdates(DEFAULT_COMPANY_CODE, TimeFrame.MINUTE_30.getTimeCode());
	}

	// 1시간봉 업데이트 (1시간마다)
	@Scheduled(fixedRate = 3600000)
	public void sendCandleUpdates1Hour() {
		sendCandleUpdates(DEFAULT_COMPANY_CODE, TimeFrame.HOUR_1.getTimeCode());
	}

	/**
	 * 지정된 종목코드와 타임프레임에 대한 캔들 업데이트 수행
	 */
	private void sendCandleUpdates(final String companyCode, final String timeFrameCode) {
		try {
			// 새로운 캔들 생성 및 기존 캔들 업데이트
			tradeHistoryService.updateCandles(companyCode);

			// 업데이트된 캔들 데이터 조회
			final ChartResponseDto candleData = tradeHistoryService.getChartHistory(companyCode, timeFrameCode);

			// 클라이언트에 WebSocket으로 데이터 전송
			messagingTemplate.convertAndSend("/topic/candle/" + companyCode + "/" + timeFrameCode, candleData);
			log.debug("종목에 대한 캔들 업데이트 성공: {}, timeframe: {}, candles size: {}",
					companyCode, timeFrameCode, candleData.candles().size());
		} catch (Exception e) {
			log.error("종목에 대한 캔들 업데이트 실패: {}, timeframe: {}",
					companyCode, timeFrameCode, e);
		}
	}

}
