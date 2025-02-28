package org.scoula.backend.order.controller.request;

import java.math.BigDecimal;

import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;

import lombok.Builder;

@Builder
public record OrderRequest(
		String companyCode,
		Type type,
		BigDecimal totalQuantity,
		BigDecimal remainingQuantity,
		OrderStatus status,
		BigDecimal price,
		Long accountId
) {

}
