package org.scoula.backend.member.service;

import java.math.BigDecimal;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.order.domain.Type;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class AccountService {

	private final AccountRepositoryImpl accountRepository;

	public void updateAccountAfterTrade(
		final Type type, final Account account, final BigDecimal price, final BigDecimal quantity) {
		account.processOrder(type, price, quantity);
		accountRepository.save(account);
	}
}
