package org.scoula.backend.order.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record ChartResponseDto(
		List<CandleDto> candles,
		String timeCode
) {
}
