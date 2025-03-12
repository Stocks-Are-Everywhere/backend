package org.scoula.backend.concurrent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.scoula.backend.member.repository.impls.HoldingsRepositoryImpl;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.service.OrderRepository;
import org.scoula.backend.order.service.OrderService;
import org.scoula.backend.order.service.TradeHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class IntegrationConcurrentTest {

    private static final int THREAD_COUNT = 500;
    private static final String TEST_USERNAME = "username";
    private static final BigDecimal INITIAL_QUANTITY = new BigDecimal("10000");
    private static final BigDecimal TEST_PRICE = new BigDecimal("1000");
    private static final BigDecimal TEST_ORDER_QUANTITY = BigDecimal.valueOf(1);
    private static final String TEST_COMPANY_CODE = "test";

    @Autowired private OrderService orderService;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private TradeHistoryService tradeHistoryService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private HoldingsRepositoryImpl holdingsRepository;

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
            .closingPrice(TEST_PRICE)
            .build();
    private final Member testMember = Member.builder()
            .username("username1")
            .googleId("googleId1")
            .email("email1")
            .role(MemberRoleEnum.USER)
            .build();
    private final Member testMember2 = Member.builder()
            .username("username2")
            .googleId("googleId2")
            .email("email2")
            .role(MemberRoleEnum.USER)
            .build();

    @BeforeEach
    public void setup() throws InterruptedException {
        testMember.createAccount();
        testMember2.createAccount();
        memberRepository.save(testMember);
        memberRepository.save(testMember2);
        companyRepository.save(testCompany);
        Thread.sleep(500);

        Holdings holdings = Holdings.builder()
                .companyCode(testCompany.getIsuCd())
                .quantity(new BigDecimal(THREAD_COUNT * 10))
                .reservedQuantity(BigDecimal.ZERO)
                .averagePrice(TEST_PRICE)
                .totalPurchasePrice(TEST_PRICE)
                .account(testMember.getAccount())
                .build();
        Holdings holdings2 = Holdings.builder()
                .companyCode(testCompany.getIsuCd())
                .quantity(new BigDecimal(THREAD_COUNT * 10))
                .reservedQuantity(BigDecimal.ZERO)
                .averagePrice(TEST_PRICE)
                .totalPurchasePrice(TEST_PRICE)
                .account(testMember2.getAccount())
                .build();
        holdingsRepository.save(holdings);
        holdingsRepository.save(holdings2);
    }

    @Test
    void shouldProcessConcurrentOrdersCorrectly() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT * 2);

        AtomicInteger sellExceptionCount = processSellOrders(executorService, latch);
        Thread.sleep(5000);

        System.out.println("sell start");

        AtomicInteger buyExceptionCount = processBuyOrders(executorService, latch);

        latch.await();
        executorService.shutdown();

        Thread.sleep(5000);

        verifyTradeResults(buyExceptionCount.get(), sellExceptionCount.get());
    }

    private AtomicInteger processBuyOrders(ExecutorService executorService, CountDownLatch latch) {
        List<OrderRequest> buyOrders = createOrderRequests(Type.BUY, testMember);
        AtomicInteger exceptionCount = new AtomicInteger();
        
        submitOrders(executorService, latch, buyOrders, exceptionCount, testMember);
        return exceptionCount;
    }

    private AtomicInteger processSellOrders(ExecutorService executorService, CountDownLatch latch) {
        List<OrderRequest> sellOrders = createOrderRequests(Type.SELL, testMember2);
        AtomicInteger exceptionCount = new AtomicInteger();
        
        submitOrders(executorService, latch, sellOrders, exceptionCount, testMember2);
        return exceptionCount;
    }

    private void submitOrders(ExecutorService executorService, CountDownLatch latch, 
                            List<OrderRequest> orders, AtomicInteger exceptionCount, Member member) {
        for (int i = 0; i < orders.size(); i++) {
            OrderRequest order = orders.get(i);
            int finalI = i;
            executorService.execute(() -> {
                try {
                    orderService.placeOrder(order, member.getUsername());
                } catch (Exception e) {
                    System.out.println(finalI + " [Exception] " + e.fillInStackTrace() + ": " + e.getMessage());
                    exceptionCount.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private List<OrderRequest> createOrderRequests(Type type, Member member) {
        List<OrderRequest> orders = new ArrayList<>();
        Account account = memberRepository.getByUsername(member.getUsername()).getAccount();
        for (int i = 0; i < THREAD_COUNT; i++) {
            orders.add(createOrderRequest(type, TEST_ORDER_QUANTITY, TEST_PRICE, account));
//            orders.add(createOrderRequest(type, TEST_ORDER_QUANTITY, TEST_PRICE.add(new BigDecimal("100"))));
        }
        return orders;
    }

    private OrderRequest createOrderRequest(Type type, BigDecimal quantity, BigDecimal price, Account account) {
        return new OrderRequest(
                TEST_COMPANY_CODE,
                type,
                quantity,
                quantity,
                OrderStatus.ACTIVE,
                price,
                account.getId()
        );
    }

    private void verifyTradeResults(int buyExceptionCount, int sellExceptionCount) {
        List<TradeHistoryResponse> histories = tradeHistoryService.getTradeHistory();
        int expectedSuccessfulTrades = THREAD_COUNT - Math.max(buyExceptionCount, sellExceptionCount);
        
        assertThat(histories).hasSize(expectedSuccessfulTrades);
        verifyUniqueMatching(histories);
    }

    private void verifyUniqueMatching(List<TradeHistoryResponse> histories) {
        boolean[] ordersMatched = new boolean[THREAD_COUNT * 2 + 1];
        
        histories.forEach(history -> {
            assertThat(ordersMatched[Math.toIntExact(history.sellOrderId())]).isFalse();
            assertThat(ordersMatched[Math.toIntExact(history.buyOrderId())]).isFalse();
            ordersMatched[Math.toIntExact(history.sellOrderId())] = true;
            ordersMatched[Math.toIntExact(history.buyOrderId())] = true;
        });
    }
}
