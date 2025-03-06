package org.scoula.backend.member.domain;

import static jakarta.persistence.FetchType.*;

import java.math.BigDecimal;

import org.scoula.backend.global.entity.BaseEntity;
import org.scoula.backend.member.exception.InsufficientBalanceException;

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

	@OneToOne(fetch = EAGER)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

    public Account(final Member member) {
        this.member = member;
        this.balance = new BigDecimal("100000000");  // 초기 잔액 설정
    }


	public boolean hasEnoughBalance(final BigDecimal amount) {
		return this.balance.compareTo(amount) >= 0;
	}

	public void processBuyOrder(final BigDecimal amount) {
		validateDepositBalance(amount);
		this.balance = this.balance.subtract(amount);
	}


	public void processSellOrder(final BigDecimal amount) {
		this.balance = this.balance.add(amount);
	}


	private void validateDepositBalance(final BigDecimal amount) {
		if (this.balance.compareTo(amount) < 0) {
			throw new InsufficientBalanceException(
					String.format("주문금액(%s)이 예수금잔액(%s)을 초과합니다",
							amount, this.balance));
		}
	}
}
