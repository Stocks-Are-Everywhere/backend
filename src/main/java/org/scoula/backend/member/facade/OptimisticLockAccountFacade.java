package org.scoula.backend.member.facade;

import lombok.RequiredArgsConstructor;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.order.domain.Type;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class OptimisticLockAccountFacade {

    private final AccountService accountService;

    public void updateAccountWithOptimisticLock(final Long memberId, final Type type, final BigDecimal price, final BigDecimal quantity) throws InterruptedException {
        while (true) {
            try {
                accountService.updateAccountAfterTrade(memberId, type, price, quantity);
                break;
            } catch (Exception e) {
                Thread.sleep(50);
            }
        }
    }
}
