package org.scoula.backend.order.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;

// 가격대별 주문 정보
@Data
@AllArgsConstructor
public class OrderSummaryResponse {
	private final String companyCode;
	private final Integer sellCount;
	private final Integer buyCount;

}
