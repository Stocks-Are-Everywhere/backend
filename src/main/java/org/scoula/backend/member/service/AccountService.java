package org.scoula.backend.member.service;

import java.math.BigDecimal;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.order.domain.Type;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class AccountService {

	private final AccountRepository accountRepository;

	@Transactional
	public void updateAccountAfterTrade(final Long memberId, final Type type, final BigDecimal price, final BigDecimal quantity) {
		Account account = accountRepository.getByMemberId(memberId);
		account.processOrder(type, price, quantity);
	}
}