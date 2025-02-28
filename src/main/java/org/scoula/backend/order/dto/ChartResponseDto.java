package org.scoula.backend.order.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChartResponseDto {
	private List<CandleDto> candles;
}
