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
    private final AccountRepository accountRepository;
    private final HoldingsRepository holdingsRepository;

    public void updateHoldingsAfterTrade(Type type, Account account, String companyCode,
                                 BigDecimal price, BigDecimal quantity) {
        Holdings holdings =  getOrCreateHoldings(account.getId(), companyCode);
        holdings.updateHoldings(type, price, quantity);
        saveHoldings(holdings);
    }

    private void saveHoldings(final Holdings holdings) {
        holdingsRepository.save(holdings);
    }

    private Holdings getOrCreateHoldings(final Long accountId, final String companyCode) {
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
