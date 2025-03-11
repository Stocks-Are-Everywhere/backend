package org.scoula.backend.order.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

@Builder
public record KisStockHogaDto(
		// 기본 정보
		String stockCode, // 유가증권 단축 종목코드
		String businessHour,       // 영업 시간
		String hourClassCode,      // 시간 구분 코드 (장중, 장전예상 등)

		// 호가 정보
		List<BigDecimal> askPrices,   // 매도호가 1-10
		List<BigDecimal> bidPrices,    // 매수호가 1-10
		List<BigDecimal> askRemains,     // 매도호가 잔량 1-10
		List<BigDecimal> bidRemains,     // 매수호가 잔량 1-10

		// 총 잔량 정보
		Long totalAskRemain,       // 총 매도호가 잔량
		Long totalBidRemain,       // 총 매수호가 잔량
		Long overtimeTotalAskRemain, // 시간외 총 매도호가 잔량
		Long overtimeTotalBidRemain, // 시간외 총 매수호가 잔량

		// 예상 체결 정보
		Double anticipatedPrice,    // 예상 체결가
		Long anticipatedQuantity,   // 예상 체결량
		Long anticipatedVolume,     // 예상 거래량
		Double anticipatedCompared, // 예상 체결 대비
		String anticipatedComparedSign, // 예상 체결 대비 부호
		Double anticipatedComparedRate, // 예상 체결 전일 대비율

		// 기타 거래 정보
		Long accumulatedVolume,    // 누적 거래량

		// 증감 정보
		Long totalAskRemainChange, // 총 매도호가 잔량 증감
		Long totalBidRemainChange, // 총 매수호가 잔량 증감
		Long overtimeTotalAskRemainChange, // 시간외 총 매도호가 증감
		Long overtimeTotalBidRemainChange  // 시간외 총 매수호가 증감
) {
}
