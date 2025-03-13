package org.scoula.backend.member.service;


import java.math.BigDecimal;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.HoldingsRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.HoldingsRepository;
import org.scoula.backend.order.domain.Type;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockHoldingsService {

    private final AccountRepositoryImpl accountRepository;
    private final HoldingsRepositoryImpl holdingsRepository;

    public void updateHoldingsAfterTrade(final Type type, final Account account, final String companyCode,
                                         final BigDecimal price, final BigDecimal quantity) {
        final Holdings holdings = getOrCreateHoldings(account.getId(), companyCode);
        holdings.updateHoldings(type, price, quantity);
        saveHoldings(holdings);
    }

    public void saveHoldings(final Holdings holdings) {
        holdingsRepository.save(holdings);
    }

    public Holdings getOrCreateHoldings(final Long accountId, final String companyCode) {
        final Account account = accountRepository.getById(accountId);
        return holdingsRepository.findByAccountIdAndCompanyCode(account.getId(), companyCode)
                .orElseGet(() -> Holdings.builder()
                        .account(account)
                        .companyCode(companyCode)
                        .quantity(BigDecimal.ZERO)
                        .reservedQuantity(BigDecimal.ZERO)
                        .averagePrice(BigDecimal.ZERO)
                        .totalPurchasePrice(BigDecimal.ZERO)
                        .build());
    }
}
