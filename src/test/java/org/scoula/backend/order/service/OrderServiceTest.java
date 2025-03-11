package org.scoula.backend.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.scoula.backend.member.exception.MemberNotFoundException;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.member.repository.impls.HoldingsRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.repository.OrderRepositoryImpl;
import org.scoula.backend.order.service.exception.MatchingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@InjectMocks
	OrderService orderService;

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

	@Mock
	OrderRepository orderRepository;

	@Mock
	StockHoldingsService stockHoldingsService;

	@Mock
	AccountService accountService;



	private final Company company = Company.builder().isuNm("AAPL").isuCd("AAPL").closingPrice(new BigDecimal("150.00")).build();
	private final Member member = Member.builder().id(1L).username("username").googleId("googleId").role(MemberRoleEnum.USER).build();
	private Account account;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		orderService = new OrderService(messagingTemplate, tradeHistoryService, companyRepository, accountRepository,
			memberRepository, orderRepository, holdingsRepository,  stockHoldingsService, accountService);

		member.createAccount();
	}

	@Test
	@DisplayName("TC20.2.1 주문 생성 테스트")
	void testPlaceOrder() throws MatchingException {
		OrderRequest request = OrderRequest.builder()
			.companyCode("AAPL")
			.type(Type.BUY)
			.totalQuantity(new BigDecimal("10"))
			.remainingQuantity(new BigDecimal("10"))
			.status(OrderStatus.ACTIVE)
			.price(new BigDecimal("150.00"))
			.accountId(1L)
			.build();
		when(companyRepository.findByIsuSrtCd("AAPL")).thenReturn(Optional.of(company));
		when(memberRepository.getByUsername(any())).thenReturn(member);

		orderService.placeOrder(request, "test");

		verify(messagingTemplate).convertAndSend(eq("/topic/orderbook/AAPL"), any(OrderBookResponse.class));
	}

	@Test
	@DisplayName("TC20.2.2 주문장 스냅샷 조회 테스트")
	void testGetSnapshot() {
		String companyCode = "AAPL";

		OrderSnapshotResponse response = orderService.getSnapshot(companyCode);

		assertNotNull(response);
		assertEquals(companyCode, response.companyCode());
	}

	@Test
	@DisplayName("TC20.2.3 호가창 조회 테스트")
	void testGetBook() {
		String companyCode = "AAPL";

		OrderBookResponse response = orderService.getBook(companyCode);

		assertNotNull(response);
		assertEquals(companyCode, response.companyCode());
	}

	// @Test
	// @DisplayName("TC20.2.4 주문 요약 조회 테스트")
	// void testGetSummary() {
	// 	String companyCode = "AAPL";
	//
	// 	OrderSummaryResponse response = orderService.getSummary(companyCode);
	//
	// 	assertNotNull(response);
	// 	assertEquals(companyCode, response.companyCode());
	// }

	@Test
	@DisplayName("TC20.2.5 거래 내역 조회 테스트")
	void testGetTradeHistory() {
		orderService.getTradeHistory();

		verify(tradeHistoryService).getTradeHistory();
	}

	// @Test
	// @DisplayName("TC20.2.6 트랜잭션 ACID 속성 - 원자성 테스트")
	// @Transactional
	// void testAtomicity() {
	// 	OrderRequest buyOrder = OrderRequest.builder()
	// 		.companyCode("AAPL")
	// 		.type(Type.BUY)
	// 		.totalQuantity(new BigDecimal("10"))
	// 		.remainingQuantity(new BigDecimal("10"))
	// 		.status(OrderStatus.ACTIVE)
	// 		.price(new BigDecimal("150.00"))
	// 		.accountId(1L)
	// 		.build();
	// 	OrderRequest invalidSellOrder = null;
	//
	// 	assertThrows(IllegalArgumentException.class, () -> {
	// 		orderService.placeOrder(buyOrder);
	// 		orderService.placeOrder(invalidSellOrder);
	// 	});
	//
	// 	OrderBookResponse response = orderService.getBook("AAPL");
	// 	assertTrue(response.buyLevels().isEmpty(), "롤백으로 인해 주문이 없어야 함");
	// }

	@Test
	@DisplayName("TC20.2.7 트랜잭션 ACID 속성 - 일관성 테스트")
	@Transactional
	void testConsistency() throws MatchingException {
		OrderRequest buyOrder = OrderRequest.builder()
			.companyCode("AAPL")
			.type(Type.BUY)
			.totalQuantity(new BigDecimal("10"))
			.remainingQuantity(new BigDecimal("10"))
			.status(OrderStatus.ACTIVE)
			.price(new BigDecimal("150.00"))
			.accountId(1L)
			.build();
		when(companyRepository.findByIsuSrtCd("AAPL")).thenReturn(Optional.of(company));
		when(memberRepository.getByUsername(any())).thenReturn(member);

		orderService.placeOrder(buyOrder, "test");

		OrderBookResponse response = orderService.getBook("AAPL");
		assertEquals(1, response.buyLevels().size());
		assertEquals(new BigDecimal("150.00"), response.buyLevels().get(0).price());
		assertEquals(new BigDecimal("10"), response.buyLevels().get(0).quantity());
	}

//	@Test
//	@DisplayName("TC20.2.8 트랜잭션 ACID 속성 - 격리성 테스트")
//	@Transactional
//	void testIsolation() throws InterruptedException {
//		CountDownLatch latch = new CountDownLatch(2);
//		AtomicReference<OrderBookResponse> response1 = new AtomicReference<>();
//		AtomicReference<OrderBookResponse> response2 = new AtomicReference<>();
//
//		when(companyRepository.findByIsuSrtCd("AAPL")).thenReturn(Optional.of(company));
//		when(memberRepository.getByUsername(any())).thenReturn(member);
//
//		Thread t1 = new Thread(() -> {
//			try {
//				OrderRequest buyOrder = OrderRequest.builder()
//					.companyCode("AAPL")
//					.type(Type.BUY)
//					.totalQuantity(new BigDecimal("10"))
//					.remainingQuantity(new BigDecimal("10"))
//					.status(OrderStatus.ACTIVE)
//					.price(new BigDecimal("150.00"))
//					.accountId(1L)
//					.build();
//				orderService.placeOrder(buyOrder, "test");
//				response1.set(orderService.getBook("AAPL"));
//				latch.countDown();
//			} catch (MatchingException e) {
//				e.printStackTrace();
//			}
//		});
//
//		Thread t2 = new Thread(() -> {
//			try {
//				OrderRequest sellOrder = OrderRequest.builder()
//					.companyCode("AAPL")
//					.type(Type.SELL)
//					.totalQuantity(new BigDecimal("5"))
//					.remainingQuantity(new BigDecimal("5"))
//					.status(OrderStatus.ACTIVE)
//					.price(new BigDecimal("151.00"))
//					.accountId(2L)
//					.build();
//				orderService.placeOrder(sellOrder, "test");
//				response2.set(orderService.getBook("AAPL"));
//				latch.countDown();
//			} catch (MatchingException e) {
//				e.printStackTrace();
//			}
//		});
//
//		t1.start();
//		t2.start();
//		latch.await();
//
//		assertEquals(1, response1.get().buyLevels().size());
//		assertEquals(1, response2.get().sellLevels().size());
//	}

	@Test
	@DisplayName("입력받은 사용자에 대한 정보가 저장되어있지 않은 경우 예외를 반환한다.")
	void orderFailedWhenMemberNotFound() {
		// given
		Company company = Company.builder().isuNm("삼성전자").isuCd("005930").closingPrice(new BigDecimal(1000)).build();
		when(memberRepository.getByUsername(any())).thenThrow(MemberNotFoundException.class);
		when(companyRepository.findByIsuSrtCd(any())).thenReturn(Optional.of(company));
		OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(1000));

		// when, then
		assertThatThrownBy(() -> orderService.placeOrder(request, "username"))
			.isInstanceOf(MemberNotFoundException.class);
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

	@Test
	@DisplayName("매도 주문 시 보유 주식이 없다면 예외를 반환한다.")
	void sellOrderFailedWhenUserHasNoStock() {}

	@Test
	@DisplayName("매도 주문 시 주문 가능한 매도 가능 수량을 초과하면 예외를 반환한다.")
	void sellOrderFailedWhenSellOrderQuantityExceedsAvailableQuantity() {}

	@Test
	@DisplayName("매수 주문 시 사용자 잔액이 부족한 경우 예외를 반환한다.")
	void BuyOrderFailedWhenUserHasInsufficientBalance() {}
}
