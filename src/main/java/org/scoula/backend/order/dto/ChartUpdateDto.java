package org.scoula.backend.order.dto;

import lombok.Builder;

@Builder
public record ChartUpdateDto(
		Double price,
		Integer volume,
		String timeCode
) {
}
