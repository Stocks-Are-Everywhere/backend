package org.scoula.backend.order.dto;

import java.math.BigDecimal;

// 가격대별 주문 정보
public record PriceLevelDto(
		BigDecimal price,
		BigDecimal quantity,
		Integer orderCount
) {
}
