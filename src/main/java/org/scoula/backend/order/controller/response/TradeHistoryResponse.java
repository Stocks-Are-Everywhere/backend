package org.scoula.backend.order.controller.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record TradeHistoryResponse(
		Long id,
		String companyCode,
		Long sellOrderId,
		Long buyOrderId,
		BigDecimal quantity,
		BigDecimal price,
		LocalDateTime tradeTime
) {
}
