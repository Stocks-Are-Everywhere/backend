package org.scoula.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.Arrays;
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
    private static final BigDecimal TEST_ORDER_QUANTITY = BigDecimal.valueOf(10);
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

        Holdings holdings = Holdings.builder()
                .companyCode(testCompany.getIsuCd())
                .quantity(INITIAL_QUANTITY)
                .reservedQuantity(BigDecimal.ZERO)
                .averagePrice(TEST_PRICE)
                .totalPurchasePrice(TEST_PRICE)
                .account(testMember.getAccount())
                .build();
        holdingsRepository.save(holdings);
    }

    @Test
    void shouldProcessConcurrentOrdersCorrectly() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT * 2);

        long startBuy = System.currentTimeMillis();
        AtomicInteger sellExceptionCount = processSellOrders(executorService, latch);
        long endBuy = System.currentTimeMillis();

        Thread.sleep(5000);
        System.out.println("sell start");
        long startSell = System.currentTimeMillis();

        AtomicInteger buyExceptionCount = processBuyOrders(executorService, latch);

        latch.await();
        executorService.shutdown();
        long endSell = System.currentTimeMillis();
        Thread.sleep(5000);

        long durationTimeSec = (endBuy - startBuy) + (endSell - startSell);

        System.out.println(durationTimeSec + "m/s");
        System.out.println((durationTimeSec / 1000) + "sec");

        verifyTradeResults(buyExceptionCount.get(), sellExceptionCount.get());
    }

    private AtomicInteger processBuyOrders(ExecutorService executorService, CountDownLatch latch) {
        List<OrderRequest> buyOrders = createOrderRequests(Type.BUY);
        AtomicInteger exceptionCount = new AtomicInteger();
        
        submitOrders(executorService, latch, buyOrders, exceptionCount);
        return exceptionCount;
    }

    private AtomicInteger processSellOrders(ExecutorService executorService, CountDownLatch latch) {
        List<OrderRequest> sellOrders = createOrderRequests(Type.SELL);
        AtomicInteger exceptionCount = new AtomicInteger();
        
        submitOrders(executorService, latch, sellOrders, exceptionCount);
        return exceptionCount;
    }

    private void submitOrders(ExecutorService executorService, CountDownLatch latch, 
                            List<OrderRequest> orders, AtomicInteger exceptionCount) {
        for (int i = 0; i < orders.size(); i++) {
            OrderRequest order = orders.get(i);
            int finalI = i;
            executorService.execute(() -> {
                try {
                    orderService.placeOrder(order, TEST_USERNAME);
                } catch (Exception e) {
                    System.out.println(finalI + " [Exception] " + e.fillInStackTrace() + ": " + e.getMessage());
                    Arrays.stream(e.getSuppressed()).forEach(suppressed -> System.out.println("Suppressed: " + suppressed));
                    exceptionCount.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private List<OrderRequest> createOrderRequests(Type type) {
        List<OrderRequest> orders = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            orders.add(createOrderRequest(type, TEST_ORDER_QUANTITY, TEST_PRICE));
//            orders.add(createOrderRequest(type, TEST_ORDER_QUANTITY, TEST_PRICE.add(new BigDecimal("100"))));
        }
        return orders;
    }

    private OrderRequest createOrderRequest(Type type, BigDecimal quantity, BigDecimal price) {
        return new OrderRequest(
                TEST_COMPANY_CODE,
                type,
                quantity,
                quantity,
                OrderStatus.ACTIVE,
                price,
                testMember.getAccount().getId()
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
