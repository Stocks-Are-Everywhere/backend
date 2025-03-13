package org.scoula.backend.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.fake.TestOrderRepository;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.repository.impls.HoldingsRepositoryImpl;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class FakeDoubleTest {

    OrderService orderService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    AccountRepository accountRepository;

    @Mock
    MemberRepository memberRepository;

    @Mock
    CompanyRepository companyRepository;

    @Mock
    HoldingsRepositoryImpl holdingsRepository;

    @Mock
    TradeHistoryService tradeHistoryService;

    OrderRepository orderRepository = new TestOrderRepository();

    @Mock
    StockHoldingsService stockHoldingsService;

    @Mock
    AccountService accountService;

    private final Company company = Company.builder().isuNm("005930").isuCd("005930").closingPrice(new BigDecimal("1000")).build();
    private Member sellMember;
    private Account account;
    private Member buyMember;
    private Account account2;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                messagingTemplate,
                tradeHistoryService,
                companyRepository,
                memberRepository,
                orderRepository,
                holdingsRepository
        );
        sellMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .build();
        account = new Account(1L, new BigDecimal(10000), new BigDecimal(10), sellMember, 0L);
        sellMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .account(account)
                .build();

        buyMember = Member.builder()
                .email("test2@example.com")
                .username("testuser2")
                .build();
        account2 = new Account(2L, new BigDecimal(10000), new BigDecimal(10), buyMember, 0L);
        buyMember = Member.builder()
                .id(2L)
                .email("test@example.com")
                .username("testuser")
                .account(account2)
                .build();
    }

//    @Test
//    @DisplayName("체결이 완료 되면 remaining quantity가 감소한다.")
//    void success() {
//        // given
//        OrderRequest buyRequest = createOrderRequest(Type.BUY, BigDecimal.ONE, new BigDecimal(1000));
//        OrderRequest sellRequest = createOrderRequest(Type.SELL, BigDecimal.ONE, new BigDecimal(1000));
//        when(companyRepository.findByIsuSrtCd("005930")).thenReturn(Optional.of(company));
//        when(memberRepository.getByUsername(sellMember.getUsername())).thenReturn(sellMember);
//        when(holdingsRepository.findByAccountIdAndCompanyCode(buyMember.getAccount().getId(), "005930")).thenReturn(
//                Optional.ofNullable(Holdings.builder().id(1L).account(sellMember.getAccount()).quantity(new BigDecimal(100)).companyCode("005930").reservedQuantity(BigDecimal.ZERO).averagePrice(BigDecimal.TEN).totalPurchasePrice(BigDecimal.TEN).build())
//        );
//        when(memberRepository.getByUsername(buyMember.getUsername())).thenReturn(buyMember);
//
//        // when
//        orderService.placeOrder(buyRequest, buyMember.getUsername());
//        orderService.placeOrder(sellRequest, sellMember.getUsername());
//
//        // then
//        Order buyOrder = orderRepository.getById(1L);
//        Order sellOrder = orderRepository.getById(2L);
//        System.out.println(buyOrder.getRemainingQuantity());
//        assertThat(buyOrder.getRemainingQuantity()).isZero();
//        assertThat(sellOrder.getRemainingQuantity()).isZero();
//    }

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
