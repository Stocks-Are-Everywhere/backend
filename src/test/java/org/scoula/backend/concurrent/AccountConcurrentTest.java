package org.scoula.backend.concurrent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.scoula.backend.member.facade.OptimisticLockAccountFacade;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.order.domain.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountConcurrentTest {

    private static final int THREAD_COUNT = 1000;
    private static final String TEST_USERNAME = "username";
    private static final String TEST_COMPANY_CODE = "test";

    @Autowired
    private OptimisticLockAccountFacade optimisticLockAccountFacade;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private final Company testCompany = Company.builder()
            .isuNm(TEST_COMPANY_CODE)
            .isuCd(TEST_COMPANY_CODE)
            .isuSrtCd(TEST_COMPANY_CODE)
            .isuAbbrv(TEST_COMPANY_CODE)
            .isuEngNm(TEST_COMPANY_CODE)
            .listDd(TEST_COMPANY_CODE)
            .mktTpNm(TEST_COMPANY_CODE)
            .secugrpNm(TEST_COMPANY_CODE)
            .sectTpNm(TEST_COMPANY_CODE)
            .kindStkcertTpNm(TEST_COMPANY_CODE)
            .parval(TEST_COMPANY_CODE)
            .listShrs(TEST_COMPANY_CODE)
            .closingPrice(new BigDecimal("1000"))
            .build();

    private final Member testMember = Member.builder()
            .username(TEST_USERNAME)
            .googleId("googleId")
            .email("email")
            .role(MemberRoleEnum.USER)
            .build();

    @BeforeEach
    public void setup() throws InterruptedException {
        testMember.createAccount();
        memberRepository.save(testMember);
        companyRepository.save(testCompany);
        Thread.sleep(500);
    }

    @Test
    @DisplayName("동시에 여러번 계좌를 업데이트 할 때, 모두 정상 반영 된다.")
    void updateAccountConcurrently() throws InterruptedException {
        Member member = memberRepository.getByUsername(TEST_USERNAME);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        for (int i = 0; i < THREAD_COUNT; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    optimisticLockAccountFacade.updateAccountWithOptimisticLock(member.getId(), Type.BUY, BigDecimal.ONE, BigDecimal.ONE);
                    System.out.println(finalI);
                } catch (Exception e) {
                    System.out.println(finalI + " [Exception] " + e.fillInStackTrace() + ": " + e.getMessage());
                    Arrays.stream(e.getSuppressed()).forEach(suppressed -> System.out.println("Suppressed: " + suppressed));
                    exceptionCount.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        Account end = accountRepository.getById(1L);
        assertThat(end.getReservedBalance().abs().intValue()).isEqualTo(THREAD_COUNT);
    }
}
