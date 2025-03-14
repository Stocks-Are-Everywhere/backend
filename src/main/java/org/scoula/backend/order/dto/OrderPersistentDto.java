package org.scoula.backend.order.dto;

import java.math.BigDecimal;

import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;

public record OrderPersistentDto(
		Long id,
		String companyCode,
		String type,
		BigDecimal totalQuantity,
		BigDecimal remainingQuantity,
		String status,
		BigDecimal price,
		Long accountId,  // 객체 참조 대신 ID만 저장
		Long timestamp
) {
	// Jackson 직렬화용 기본 생성자
	public OrderPersistentDto() {
		this(null, null, null, null, null, null, null, null, null);
	}

	// Order 객체로부터 DTO 생성
	public OrderPersistentDto(Order order) {
		this(
				order.getId(),
				order.getCompanyCode(),
				order.getType().name(),
				order.getTotalQuantity(),
				order.getRemainingQuantity(),
				order.getStatus().name(),
				order.getPrice(),
				order.getAccount().getId(),
				order.getTimestamp()
		);
	}

	// DTO에서 Order로 변환 (Account 객체는 별도로 조회 필요)
	public Order toOrderEntity() {
		// Order 빌더를 사용하여 Order 객체 생성
		return Order.builder()
				.id(this.id)
				.companyCode(this.companyCode)
				.type(Type.valueOf(this.type))
				.totalQuantity(this.totalQuantity)
				.remainingQuantity(this.remainingQuantity)
				.status(OrderStatus.valueOf(this.status))
				.price(this.price)
				.accountId(this.accountId)
				.timestamp(this.timestamp)
				.build();
	}
}
