package org.scoula.backend.order.controller.request;

import java.math.BigDecimal;

import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;

import lombok.Builder;

@Builder
public record OrderRequest(
		String companyCode,
		Type type,
		// Question: 주문 생성에 totalQuantity와 remainingQuantity가 둘 다 필요한지 확인 필요.(client 설정 확인 필요) - 주문시에는 요청 수량만 필요할 것으로 보임.
		BigDecimal totalQuantity,
		BigDecimal remainingQuantity,
		OrderStatus status,
		BigDecimal price,
		Long accountId
) {

}
