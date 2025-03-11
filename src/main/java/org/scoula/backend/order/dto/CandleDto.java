package org.scoula.backend.order.dto;

import lombok.Builder;

@Builder
public record CandleDto(
		Long time,
		Double open,     // 시가
		Double high,     // 고가
		Double low,      // 저가
		Double close,    // 종가
		Integer volume      // 거래량
) {

}
