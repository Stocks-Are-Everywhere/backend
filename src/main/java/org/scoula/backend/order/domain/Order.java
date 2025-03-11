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

	@Column(nullable = false)
	private Long timestamp;

	// BigDecimal는 불변 객체 입니다.
	public void decreaseRemainingQuantity(final BigDecimal quantity) {
		this.remainingQuantity = this.remainingQuantity.subtract(quantity);
		if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			this.status = OrderStatus.COMPLETE;
		}
	}

	public boolean isSellType() {
		return type == Type.SELL;
	}
}
