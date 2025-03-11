package org.scoula.backend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
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

    @Autowired
    OrderService orderService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    TradeHistoryService tradeHistoryService;

    @Autowired
    CompanyRepositoryImpl companyRepository;

    @Autowired
    AccountRepositoryImpl accountRepository;

    @Autowired
    MemberRepositoryImpl memberRepository;

    @Autowired
    OrderRepository orderRepository;

    private final Company company
            = Company.builder()
            .isuNm("005930")
            .isuCd("005930")
            .isuSrtCd("005930")
            .isuNm("005930")
            .isuAbbrv("005930")
            .isuEngNm("005930")
            .listDd("005930")
            .mktTpNm("005930")
            .secugrpNm("005930")
            .sectTpNm("005930")
            .kindStkcertTpNm("005930")
            .parval("005930")
            .listShrs("005930")
            .closingPrice(new BigDecimal("1000"))
            .build();
    Member member = Member.builder().username("test").googleId("test").email("test").role(MemberRoleEnum.USER).build();

    @BeforeEach
    public void setup() {
        member.createAccount();
        companyRepository.save(company);
        memberRepository.save(member);
    }


    @Test
    void concurrentOrderServiceProcessing() throws InterruptedException {
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<OrderRequest> buyOrders = createBuyOrderReuest();
        AtomicInteger buyExceptionCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    orderService.placeOrder(buyOrders.get(finalI), "test");
                } catch (Exception e) {
                    System.out.println("exception: " + e.getMessage());
                    buyExceptionCount.getAndIncrement();
                }
                finally{
                    latch.countDown();
                }
            });
        }

        Thread.sleep(5000);

        AtomicInteger sellExceptionCount = new AtomicInteger();
        List<OrderRequest> sellOrders = createSellOrderRequest();
        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    orderService.placeOrder(sellOrders.get(finalI), "test");
                } catch (Exception e) {
                    System.out.println("exception: " + e.getMessage());
                    sellExceptionCount.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();
        Thread.sleep(5000);

        int count = Math.max(sellExceptionCount.get(), buyExceptionCount.get());
        List<TradeHistoryResponse> histories = tradeHistoryService.getTradeHistory();
        assertThat(tradeHistoryService.getTradeHistory()).hasSize(threadCount - Math.max(sellExceptionCount.get(), buyExceptionCount.get()));
    }

    private List<OrderRequest> createBuyOrderReuest() {
        List<OrderRequest> orders = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(1000));
            orders.add(request);
        }
        return orders;
    }

    private List<OrderRequest> createSellOrderRequest() {
        List<OrderRequest> orders = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            OrderRequest request = createOrderRequest(Type.SELL, BigDecimal.valueOf(10), BigDecimal.valueOf(1000));
            orders.add(request);
        }

        return orders;
    }

    private OrderRequest createOrderRequest(
            Type type,
            BigDecimal totalQuantity,
            BigDecimal price
    ) {
        return new OrderRequest(
                "005930",
                type,
                totalQuantity,
                totalQuantity,
                OrderStatus.ACTIVE,
                price,
                1L
        );
    }
}
