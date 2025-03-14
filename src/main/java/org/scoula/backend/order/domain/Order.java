package org.scoula.backend.order.domain;

import static jakarta.persistence.FetchType.*;

import java.math.BigDecimal;

import org.scoula.backend.global.entity.BaseEntity;
import org.scoula.backend.member.domain.Account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
@Table(name = "orders")
public class Order extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "order_id")
	private Long id;

	@Column(nullable = false)
	private String companyCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	@Column(nullable = false, precision = 10, scale = 0)
	private BigDecimal totalQuantity;

	@Column(nullable = false, precision = 10, scale = 0)
	private BigDecimal remainingQuantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@Column(nullable = false, precision = 10, scale = 0)
	private BigDecimal price;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	// 새 코드
	@Transient
	private Long accountId;

	@Column(nullable = false)
	private Long timestamp;

	public void decreaseRemainingQuantity(final BigDecimal quantity) {
		this.remainingQuantity = this.remainingQuantity.subtract(quantity);
		if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			this.status = OrderStatus.COMPLETE;
		}
	}

	public boolean isSellType() {
		return type == Type.SELL;
	}

	// 주문 완료 처리
	public void complete() {
		this.status = OrderStatus.COMPLETE;
	}

	// 주문 수량 변경에 따른 상태 업데이트
	public void updateStatusBasedOnQuantity() {
		if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			this.complete();
		}
	}

	// 체결 처리 (수량 감소 및 상태 업데이트)
	public void processMatch(final BigDecimal matchedQuantity) {
		this.decreaseRemainingQuantity(matchedQuantity);
		this.updateStatusBasedOnQuantity();
	}

	// 체결 완료 여부 확인
	public boolean isCompletelyFilled() {
		return this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0;
	}

}
