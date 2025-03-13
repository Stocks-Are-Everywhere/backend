package org.scoula.backend.member.domain;

import static jakarta.persistence.FetchType.*;

import java.math.BigDecimal;

import org.scoula.backend.global.entity.BaseEntity;
import org.scoula.backend.member.exception.InsufficientBalanceException;
import org.scoula.backend.order.domain.Type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Slf4j
public class Account extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "account_id")
	private Long id;

	@Column(nullable = false)
	private BigDecimal balance;

	@Column(nullable = false)
	private BigDecimal reservedBalance;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	public Account(final Member member) {
		this.member = member;
		this.balance = new BigDecimal("100000000");  // 초기 잔액 설정
		this.reservedBalance = BigDecimal.ZERO;
	}

	// 예약 주문 처리
	public void processReservedOrder(final BigDecimal amount) {
		this.reservedBalance = this.reservedBalance.add(amount);
	}

	public BigDecimal getAvailableBalance() {
		return this.balance.subtract(this.reservedBalance);
	}

	public void processOrder(final Type type, final BigDecimal price, final BigDecimal quantity) {
		final BigDecimal totalPrice = price.multiply(quantity);

		if (type.equals(Type.BUY)) {
			processBuyOrder(totalPrice);
		} else {
			processSellOrder(totalPrice);
		}
	}

	private void processBuyOrder(final BigDecimal totalPrice) {
		validateDepositBalance(totalPrice);
		this.reservedBalance = this.reservedBalance.subtract(totalPrice);
		this.balance = this.balance.subtract(totalPrice);
	}

	private void processSellOrder(final BigDecimal totalPrice) {
		this.balance = this.balance.add(totalPrice);
	}

	public void validateDepositBalance(final BigDecimal totalPrice) {
		final BigDecimal availableBalance = getAvailableBalance();
		if (availableBalance.compareTo(totalPrice) < 0) {
			throw new InsufficientBalanceException("주문금액이 예수금잔액을 초과합니다.");
		}
	}

	public void deposit(final BigDecimal price) {
		this.balance = this.balance.add(price);
	}
}
