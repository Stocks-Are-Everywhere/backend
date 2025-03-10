package org.scoula.backend.order.dto;

import java.time.Instant;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.Order;

import lombok.Builder;

@Builder
public record OrderDto(
		OrderRequest request
) {
	public Order to(final Account account) {
		final Long now = Instant.now().getEpochSecond();
		return Order.builder()
				.companyCode(request.companyCode())
				.type(request.type())
				.totalQuantity(request.totalQuantity())
				.remainingQuantity(request.remainingQuantity())
				.status(request.status())
				.price(request.price())
				.account(account)
				.timestamp(now)
				.build();
	}
}
