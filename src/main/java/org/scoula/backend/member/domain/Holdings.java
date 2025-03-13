package org.scoula.backend.member.domain;

import static jakarta.persistence.FetchType.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.scoula.backend.global.entity.BaseEntity;
import org.scoula.backend.member.exception.HoldingsNotFoundException;
import org.scoula.backend.member.exception.InsufficientHoldingsException;
import org.scoula.backend.order.domain.Type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "holdings",
       uniqueConstraints = {
           @UniqueConstraint(
               name = "uk_account_company",
               columnNames = {"account_id", "company_code"}
           )
       })
public class Holdings extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "holdings_id")
	private Long id;

	@Column(nullable = false, updatable = false)
	private String companyCode;

	@Column(nullable = false)
	private BigDecimal quantity;

	@Column(nullable = false)
	private BigDecimal reservedQuantity;

	@Column(nullable = false)
	private BigDecimal averagePrice;

	@Column(nullable = false)
	private BigDecimal totalPurchasePrice;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "account_id", nullable = false, updatable = false)
	private Account account;

	public void validateEnoughHoldings(final BigDecimal checkQuantity) {
		if (getAvailableQuantity().compareTo(checkQuantity) < 0) {
			throw new InsufficientHoldingsException("판매 가능한 보유 주식 수량이 부족합니다.");
		}
	}

	public void validateExistHoldings() {
		if (this.isDeleted()) {
			throw new HoldingsNotFoundException("보유 주식이 없습니다.");
		}
		if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
			throw new HoldingsNotFoundException("판매 가능한 보유 주식이 없습니다.");
		}
	}

	// 예약 주문 처리
	public void processReservedOrder(final BigDecimal reservedQuantity) {
		this.reservedQuantity = this.reservedQuantity.add(reservedQuantity);
	}

	public void updateHoldings(final Type type, final BigDecimal updatePrice, final BigDecimal updateQuantity) {
		if (Type.BUY.equals(type)) {
			updateBuyHoldings(updatePrice, updateQuantity);
		} else {
			updateSellHoldings(updateQuantity);
		}
	}

	private BigDecimal getAvailableQuantity() {
		return this.quantity.subtract(this.reservedQuantity);
	}

	private void updateBuyHoldings(final BigDecimal updatePrice, final BigDecimal updateQuantity) {
		if (this.isDeleted()) {
			restore();
		}
		this.quantity = this.quantity.add(updateQuantity);
		this.totalPurchasePrice = this.totalPurchasePrice.add(updateQuantity.multiply(updatePrice));
		this.averagePrice = this.totalPurchasePrice.divide(this.quantity, 4, RoundingMode.HALF_UP);

	}

	// 새로운 총 매수 금액 = 기존 총 매수 금액 − (평단가×매도 수량)
	// 손익 = (매도가 - 평단가) × 매도 수량
	private void updateSellHoldings(final BigDecimal updateQuantity) {
		// validateExistHoldings();
		this.quantity = this.quantity.subtract(updateQuantity);
		this.totalPurchasePrice = this.totalPurchasePrice.subtract(updateQuantity.multiply(this.averagePrice));

		// 예약 수량 감소 (체결된 만큼 예약 수량에서 제거)
		this.reservedQuantity = this.reservedQuantity.subtract(updateQuantity);

		if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
			this.softDelete(LocalDateTime.now());
		}
	}
}
