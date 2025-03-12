package org.scoula.backend.order.domain;

import static jakarta.persistence.FetchType.*;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

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
import jakarta.persistence.PostLoad;
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

	@Getter(AccessLevel.NONE)
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

	@Column(nullable = false)
	private Long timestamp;

	@Transient // DB에 저장되지 않도록 함
	private AtomicReference<BigDecimal> atomicRemainingQuantity;

	@PostLoad
	public void initializeAtomicReference() {
		if (atomicRemainingQuantity == null) {
			atomicRemainingQuantity = new AtomicReference<>(remainingQuantity);
		}
	}

	// CAS를 사용한 수량 감소 메서드
	public void decreaseRemainingQuantity(final BigDecimal quantity) {
		// AtomicReference가 초기화되지 않았으면 초기화
		if (atomicRemainingQuantity == null) {
			initializeAtomicReference();
		}

		BigDecimal current, updated;
		do {
			current = atomicRemainingQuantity.get();
			updated = current.subtract(quantity);
			if (updated.compareTo(BigDecimal.ZERO) < 0) {
				updated = BigDecimal.ZERO;
			}
		} while (!atomicRemainingQuantity.compareAndSet(current, updated));

		// DB 저장용 필드도 업데이트
		this.remainingQuantity = updated;

		if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			this.status = OrderStatus.COMPLETE;
		}
	}

	// getRemainingQuantity 메서드 오버라이드

	public BigDecimal getRemainingQuantity() {
		// AtomicReference가 초기화되지 않았으면 일반 필드 반환
		if (atomicRemainingQuantity == null) {
			return remainingQuantity;
		}
		return atomicRemainingQuantity.get();
	}

	public boolean isSellType() {
		return type == Type.SELL;
	}
}
