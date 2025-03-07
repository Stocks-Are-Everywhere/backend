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
	private BigDecimal reservcedBalance;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

    public Account(final Member member) {
        this.member = member;
        this.balance = new BigDecimal("100000000");  // 초기 잔액 설정
		this.reservcedBalance = new BigDecimal("0");
    }

	public void addPendingOrderBalance(final BigDecimal amount) {
		this.reservcedBalance = this.reservcedBalance.add(amount);
	}

	public BigDecimal availableBalance() {
		return this.balance.subtract(this.reservcedBalance);
	}


	public boolean hasEnoughBalance(final BigDecimal amount) {
		return this.balance.compareTo(amount) >= 0;
	}

	private void processBuyOrder(final BigDecimal amount) {
		validateDepositBalance(amount);
		this.reservcedBalance = this.reservcedBalance.subtract(amount);
		this.balance = this.balance.subtract(amount);
	}

	private void processSellOrder(final BigDecimal amount) {
		this.balance = this.balance.add(amount);
	}

	public void processOrder(final Type type, final BigDecimal amount) {
		if (type.equals(Type.BUY)) {
			processBuyOrder(amount);
		} else {
			processSellOrder(amount);
		}
	}

	public void validateDepositBalance(final BigDecimal amount) {
		final BigDecimal availableBalance = availableBalance();
		if (availableBalance.compareTo(amount) < 0) {
			throw new InsufficientBalanceException("사용자 잔액이 부족합니다.");
		}
	}
}
