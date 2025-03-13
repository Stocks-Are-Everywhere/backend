package org.scoula.backend.member.service;

import java.math.BigDecimal;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.order.domain.Type;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class AccountService {

	private final AccountRepository accountRepository;

	public void updateAccountAfterTrade(final Long memberId, final Type type, final BigDecimal price, final BigDecimal quantity) {
		optimizeLoop(() -> {
			Account account = accountRepository.getByMemberId(memberId);
			account.processOrder(type, price, quantity);
			accountRepository.save(account);
		});
	}

	private void optimizeLoop(Runnable run) {
		while (true) {
			try {
				run.run();
				break;
			} catch (ObjectOptimisticLockingFailureException ex) {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
}