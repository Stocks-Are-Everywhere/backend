package org.scoula.backend.member.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.exception.AccountNotFoundException;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.order.domain.Type;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceTest {

    AccountService accountService;
    AccountRepository accountRepository = new FakeAccountRepository();

    private final Member member = Member.builder()
            .id(100L)
            .email("test@example.com")
            .username("testuser")
            .build();

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
        member.createAccount();
        accountRepository.save(member.getAccount());
    }

    @Test
    @DisplayName("메서드가 호출되면 account에서 금액을 감소시킨다.")
    void updateAccountAfterTradeSuccess() {
        // given
        Account account = accountRepository.getByMemberId(member.getId());

        // when
        accountService.updateAccountAfterTrade(Type.BUY, account, BigDecimal.ONE, BigDecimal.ONE);

        // then
        assertThat(account.getBalance()).isEqualTo(new BigDecimal(100000000).subtract(BigDecimal.ONE));
    }

    class FakeAccountRepository implements AccountRepository {

        ConcurrentSkipListSet<Account> elements = new ConcurrentSkipListSet<>(
                Comparator.comparing(Account::getId)
        );

        @Override
        public Account getByMemberId(Long memberId) {
            return elements.stream()
                    .filter(value -> value.getMember().getId().equals(memberId))
                    .findAny()
                    .orElseThrow(AccountNotFoundException::new);
        }

        @Override
        public Account getById(Long id) {
            return elements.stream()
                    .filter(value -> value.getId().equals(id))
                    .findAny()
                    .orElseThrow(AccountNotFoundException::new);
        }

        @Override
        public Account save(Account account) {
            if (account.getId() != null) {
                Account old = getById(account.getId());
                elements.remove(old);
                elements.add(account);
            }
            Account savedAccount = Account.builder()
                    .id((long) elements.size() + 1)
                    .member(account.getMember())
                    .balance(account.getBalance())
                    .reservedBalance(account.getReservedBalance())
                    .build();
            elements.add(savedAccount);
            return savedAccount;
        }
    }
}