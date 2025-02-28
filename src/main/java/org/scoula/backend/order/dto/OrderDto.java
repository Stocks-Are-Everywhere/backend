package org.scoula.backend.order.dto;

import java.time.LocalDateTime;

import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.Order;

import lombok.Builder;

@Builder
public record OrderDto(
		OrderRequest request
) {
	public Order to() {
		return Order.builder()
				.companyCode(request.companyCode())
				.type(request.type())
				.totalQuantity(request.totalQuantity())
				.remainingQuantity(request.remainingQuantity())
				.status(request.status())
				.price(request.price())
				// .account(request.accountId())
				.timestamp(LocalDateTime.now())
				.build();
	}
}
