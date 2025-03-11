package org.scoula.backend.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.global.jwt.JwtUtil;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.PriceLevelDto;
import org.scoula.backend.order.service.exception.MatchingException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderBookService 테스트")
@ActiveProfiles("test")
class OrderBookServiceTest {

	@InjectMocks
	private OrderBookService orderBookService;

	@Mock
	private TradeHistoryService tradeHistoryService;

	@Mock
	private StockHoldingsService stockHoldingsService;

	@Mock
	private AccountService accountService;

	@Captor
	private ArgumentCaptor<TradeHistoryResponse> tradeHistoryCaptor;

	@MockitoBean
	private JwtUtil jwtUtil;

	private final String COMPANY_CODE = "005930";

	private final Member member1 = Member.builder()
			.email("test@example.com")
			.username("testuser")
			.build();

	private final Member member2 = Member.builder()
			.email("test2@example.com")
			.username("testuser2")
			.build();

	private Account account1;

	private Account account2;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService, stockHoldingsService,
				accountService);

		member1.createAccount();
		account1 = member1.getAccount();

		member2.createAccount();
		account2 = member2.getAccount();
	}

	@Test
	@DisplayName("TC3.1.1 지정가 매수 주문 추가")
	@Transactional
	void testReceiveLimitBuyOrder() throws MatchingException {
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
		orderBookService.received(buyOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertFalse(response.buyLevels().isEmpty());
		assertEquals(new BigDecimal("50000"), response.buyLevels().get(0).price());
		assertEquals(new BigDecimal("10"), response.buyLevels().get(0).quantity());
	}

	@Test
	@DisplayName("TC3.2.1 지정가 매도 주문 추가")
	@Transactional
	void testReceiveLimitSellOrder() throws MatchingException {
		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
		orderBookService.received(sellOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertFalse(response.sellLevels().isEmpty());
		assertEquals(new BigDecimal("50000"), response.sellLevels().get(0).price());
		assertEquals(new BigDecimal("10"), response.sellLevels().get(0).quantity());
	}

	@Test
	@DisplayName("TC5.1 주문장 스냅샷 조회")
	@Transactional
	void testGetSnapshot() throws MatchingException {
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
		orderBookService.received(buyOrder);

		OrderSnapshotResponse snapshot = orderBookService.getSnapshot();
		assertFalse(snapshot.buyOrders().isEmpty());
		assertTrue(snapshot.sellOrders().isEmpty());
	}

	// @Test
	// @DisplayName("TC5.2 주문 요약 정보 조회")
	// @Transactional
	// void testGetSummary() throws MatchingException {
	// 	Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
	// 	Order sellOrder = createOrder(Type.SELL, new BigDecimal("51000"), new BigDecimal("5"), OrderStatus.ACTIVE);
	//
	// 	orderBookService.received(buyOrder);
	// 	orderBookService.received(sellOrder);
	//
	// 	OrderSummaryResponse summary = orderBookService.getSummary();
	// 	assertEquals(1, summary.buyOrderVolume());
	// 	assertEquals(1, summary.sellOrderVolume());
	// }
//
//	@Test
//	@DisplayName("TC3.1.3 시장가 매수 주문 처리")
//	@Transactional
//	void testMarketBuyOrder() throws MatchingException {
//		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
//		orderBookService.received(sellOrder);
//
//		Order marketBuyOrder = createOrder(Type.BUY, BigDecimal.ZERO, new BigDecimal("10"), OrderStatus.MARKET, account2);
//		orderBookService.received(marketBuyOrder);
//
//		OrderBookResponse response = orderBookService.getBook();
//		assertTrue(response.buyLevels().isEmpty());
//		assertTrue(response.sellLevels().isEmpty());
//
//		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
//	}
//
//	@Test
//	@DisplayName("TC3.2.3 시장가 매도 주문 처리")
//	@Transactional
//	void testMarketSellOrder() throws MatchingException {
//		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
//		orderBookService.received(buyOrder);
//
//		Order marketSellOrder = createOrder(Type.SELL, BigDecimal.ZERO, new BigDecimal("10"), OrderStatus.MARKET, account1);
//		orderBookService.received(marketSellOrder);
//
//		OrderBookResponse response = orderBookService.getBook();
//		assertTrue(response.buyLevels().isEmpty());
//		assertTrue(response.sellLevels().isEmpty());
//
//		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
//	}
//
//	@Test
//	@DisplayName("TC3.1.2 & TC3.2.2 부분 체결")
//	@Transactional
//	void testPartialExecution() throws MatchingException {
//		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
//		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE, account1);
//
//		orderBookService.received(buyOrder);
//		orderBookService.received(sellOrder);
//
//		OrderBookResponse response = orderBookService.getBook();
//		assertFalse(response.buyLevels().isEmpty());
//		assertTrue(response.sellLevels().isEmpty());
//		assertEquals(new BigDecimal("5"), response.buyLevels().get(0).quantity());
//
//		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
//	}

	// @Test
	// @DisplayName("TC20.1.1 트랜잭션 ACID 속성 - 원자성 테스트")
	// void testTransactionAtomicity() {
	// 	Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
	// 	Order invalidSellOrder = null;
	//
	// 	assertThrows(IllegalArgumentException.class, () -> {
	// 		orderBookService.received(buyOrder);
	// 		orderBookService.received(invalidSellOrder);
	// 	});
	//
	// 	OrderBookResponse response = orderBookService.getBook();
	// 	assertTrue(response.buyLevels().isEmpty(), "롤백으로 인해 주문이 없어야 함");
	// }

//	@Test
//	@DisplayName("TC20.1.2 트랜잭션 ACID 속성 - 일관성 테스트")
//	@Transactional
//	void testTransactionConsistency() throws MatchingException {
//		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
//		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account2);
//
//		orderBookService.received(buyOrder);
//		orderBookService.received(sellOrder);
//
//		OrderBookResponse response = orderBookService.getBook();
//		assertTrue(response.buyLevels().isEmpty());
//		assertTrue(response.sellLevels().isEmpty());
//		// 주문이 완전히 체결되어 주문장이 비어있어야 함
//	}

	// @Test
	// @DisplayName("TC20.1.3 트랜잭션 ACID 속성 - 격리성 테스트")
	// //concurrenttreemap issue
	// void testTransactionIsolation() throws InterruptedException {
	// 	int threadCount = 10;
	// 	ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
	// 	CountDownLatch latch = new CountDownLatch(threadCount);
	//
	// 	for (int i = 0; i < threadCount; i++) {
	// 		executorService.submit(() -> {
	// 			try {
	// 				Order order = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("1"),
	// 					OrderStatus.ACTIVE);
	// 				orderBookService.received(order);
	// 			} catch (Exception e) {
	// 				e.printStackTrace();
	// 			} finally {
	// 				latch.countDown();
	// 			}
	// 		});
	// 	}
	//
	// 	latch.await();
	// 	executorService.shutdown();
	//
	// 	OrderBookResponse response = orderBookService.getBook();
	// 	assertEquals(new BigDecimal("10"), response.buyLevels().get(0).quantity());
	// 	// 모든 스레드의 주문이 정확히 반영되어야 함
	// }

	private Order createOrder(Type type, BigDecimal price, BigDecimal quantity, OrderStatus status, Account account1) {
		Long now = Instant.now().getEpochSecond();
		return Order.builder()
				.companyCode(COMPANY_CODE)
				.type(type)
				.totalQuantity(quantity)
				.remainingQuantity(quantity)
				.status(status)
				.price(price)
				.timestamp(now)
				.build();
	}

//	@Test
//	@DisplayName("TC3.3 주문 불균형 상황 테스트")
//	@Transactional
//	void testOrderImbalance() throws MatchingException {
//		// 다수의 매수 주문 생성
//		for (int i = 0; i < 5; i++) {
//			Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
//			orderBookService.received(buyOrder);
//		}
//
//		// 소수의 매도 주문 생성
//		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE, account1);
//		orderBookService.received(sellOrder);
//
//		OrderBookResponse response = orderBookService.getBook();
//		assertFalse(response.buyLevels().isEmpty());
//		assertTrue(response.sellLevels().isEmpty());
//		assertEquals(new BigDecimal("45"), response.buyLevels().get(0).quantity());
//
//		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
//	}

//	@Test
//	@DisplayName("TC3.4 대량 주문 처리 테스트")
//	@Transactional
//	void testLargeOrderProcessing() throws MatchingException {
//		BigDecimal largeQuantity = new BigDecimal("1000000");
//		Order largeBuyOrder = createOrder(Type.BUY, new BigDecimal("50000"), largeQuantity, OrderStatus.ACTIVE, account1);
//		orderBookService.received(largeBuyOrder);
//
//		Order smallSellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("100"),
//				OrderStatus.ACTIVE, account2);
//		orderBookService.received(smallSellOrder);
//
//		OrderBookResponse response = orderBookService.getBook();
//		assertFalse(response.buyLevels().isEmpty());
//		assertTrue(response.sellLevels().isEmpty());
//		assertEquals(largeQuantity.subtract(new BigDecimal("100")), response.buyLevels().get(0).quantity());
//
//		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
//	}

	@Test
	@DisplayName("매수 주문의 경우 높은 가격의 주문부터 체결된다.")
	void buyOrderHigherPricePriorityMatching() throws MatchingException {
		Long createdAt = Instant.now().getEpochSecond();
		Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(2000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(0), new BigDecimal(10), createdAt,
				OrderStatus.MARKET, account2);

		orderBookService.received(buyOrder1);
		orderBookService.received(buyOrder2);
		orderBookService.received(sellOrder);

		assertThat(buyOrder1.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
		assertThat(buyOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("매도 주문의 경우 낮은 가격의 주문부터 체결된다.")
	void sellOrderLowerPricePriorityMatching() throws MatchingException {
		Long createdAt = Instant.now().getEpochSecond();
		Order sellOrder1 = createOrder(1L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order sellOrder2 = createOrder(2L, Type.SELL, new BigDecimal(2000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order buyOrder = createOrder(3L, Type.BUY, new BigDecimal(0), new BigDecimal(10), createdAt,
				OrderStatus.MARKET, account2);

		orderBookService.received(sellOrder1);
		orderBookService.received(sellOrder2);
		orderBookService.received(buyOrder);

		assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
		assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("매수 주문시, 같은 가격일 경우 먼저 주문이 들어온 주문부터 처리한다.")
	void buyOrderTimePriorityMatching() throws MatchingException {
		Long createdAt = Instant.now().getEpochSecond();
		Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt + 1,
				OrderStatus.ACTIVE, account1);
		Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.MARKET, account2);
		orderBookService.received(buyOrder1);
		orderBookService.received(buyOrder2);
		orderBookService.received(sellOrder);

		assertThat(buyOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(buyOrder2.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
		assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("매도 주문시, 같은 가격일 경우 먼저 주문이 들어온 주문부터 처리한다.")
	void sellOrderTimePriorityMatching() throws MatchingException {
		Long createdAt = Instant.now().getEpochSecond();
		Order sellOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt + 1,
				OrderStatus.ACTIVE, account1);
		Order sellOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order buyOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.MARKET, account2);

		orderBookService.received(sellOrder1);
		orderBookService.received(sellOrder2);
		orderBookService.received(buyOrder);

		assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
		assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("매수 주문시, 모든 조건이 일치할 경우 수량이 많은 주문부터 체결한다.")
	void buyOrderQuantityPriorityMatching() throws MatchingException {
		Long createdAt = Instant.now().getEpochSecond();
		Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(11), createdAt,
				OrderStatus.ACTIVE, account1);
		Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.MARKET, account2);

		orderBookService.received(buyOrder1);
		orderBookService.received(buyOrder2);
		orderBookService.received(sellOrder);

		assertThat(buyOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(buyOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(1));
		assertThat(sellOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("매도 주문시, 모든 조건이 일치할 경우 수량이 많은 주문부터 체결한다.")
	void sellOrderQuantityPriorityMatching() throws MatchingException {

		Long createdAt = Instant.now().getEpochSecond();
		Order sellOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.ACTIVE, account1);
		Order sellOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(11), createdAt,
				OrderStatus.ACTIVE, account1);
		Order buyOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
				OrderStatus.MARKET, account2);

		orderBookService.received(sellOrder1);
		orderBookService.received(sellOrder2);
		orderBookService.received(buyOrder);

		assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(1));
		assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	@Nested
	@TestMethodOrder(MethodOrderer.DisplayName.class)
	@DisplayName("8. 호가 데이터 테스트")
	class OrderBookTests {
		@Test
		@DisplayName("TC8.1.1 실시간 호가 업데이트")
		void testRealTimeOrderBookUpdate() {

			// Given
			OrderBookService orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService,
					stockHoldingsService, accountService);

			// 초기 호가창 상태 확인
			OrderBookResponse initialOrderBook = orderBookService.getBook();
			assertEquals(0, initialOrderBook.sellLevels().size(), "초기 매도 호가는 비어있어야 함");
			assertEquals(0, initialOrderBook.buyLevels().size(), "초기 매수 호가는 비어있어야 함");

			// When
			// 매도 주문 추가
			Order sellOrder1 = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"),
					OrderStatus.ACTIVE, account1);
			orderBookService.received(sellOrder1);

			// Then
			// 업데이트된 호가창 확인
			OrderBookResponse updatedOrderBook1 = orderBookService.getBook();
			assertEquals(1, updatedOrderBook1.sellLevels().size(), "매도 호가가 1개 있어야 함");
			assertEquals(0, updatedOrderBook1.buyLevels().size(), "매수 호가는 비어있어야 함");

			// When
			// 매수 주문 추가
			Order buyOrder1 = createOrder(Type.BUY, new BigDecimal("49000"), new BigDecimal("5"), OrderStatus.ACTIVE, account1);
			orderBookService.received(buyOrder1);

			// Then
			// 업데이트된 호가창 확인
			OrderBookResponse updatedOrderBook2 = orderBookService.getBook();
			assertEquals(1, updatedOrderBook2.sellLevels().size(), "매도 호가가 1개 있어야 함");
			assertEquals(1, updatedOrderBook2.buyLevels().size(), "매수 호가가 1개 있어야 함");

			// When
			// 매수 호가 정렬 확인 (높은 가격 우선)
			Order buyOrder2 = createOrder(Type.BUY, new BigDecimal("49500"), new BigDecimal("3"), OrderStatus.ACTIVE, account1);
			orderBookService.received(buyOrder2);

			// Then
			OrderBookResponse updatedOrderBook3 = orderBookService.getBook();
			List<PriceLevelDto> buyLevels = updatedOrderBook3.buyLevels();
			assertEquals(2, buyLevels.size(), "매수 호가가 2개 있어야 함");
			assertEquals(new BigDecimal("49500"), buyLevels.get(0).price(), "더 높은 가격의 매수 호가가 먼저 나와야 함");
			assertEquals(new BigDecimal("49000"), buyLevels.get(1).price(), "더 낮은 가격의 매수 호가가 나중에 나와야 함");

			// When
			// 매도 호가 정렬 확인 (낮은 가격 우선)
			Order sellOrder2 = createOrder(Type.SELL, new BigDecimal("51000"), new BigDecimal("7"), OrderStatus.ACTIVE, account1);
			orderBookService.received(sellOrder2);

			// Then
			OrderBookResponse updatedOrderBook4 = orderBookService.getBook();
			List<PriceLevelDto> sellLevels = updatedOrderBook4.sellLevels();
			assertEquals(2, sellLevels.size(), "매도 호가가 2개 있어야 함");
			assertEquals(new BigDecimal("50000"), sellLevels.get(0).price(), "더 낮은 가격의 매도 호가가 먼저 나와야 함");
			assertEquals(new BigDecimal("51000"), sellLevels.get(1).price(), "더 높은 가격의 매도 호가가 나중에 나와야 함");
		}

//		@Test
//		@DisplayName("TC8.1.2 호가 매칭 테스트")
//		void testOrderMatching() {
//			// Given
//			OrderBookService orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService,
//					stockHoldingsService, accountService);
//			doNothing().when(tradeHistoryService).saveTradeHistory(any(TradeHistoryResponse.class));
//
//			// 매도 주문 추가
//			Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE, account1);
//			orderBookService.received(sellOrder);
//
//			// 매도 주문 전에 호가창 확인
//			OrderBookResponse beforeMatchOrderBook = orderBookService.getBook();
//			assertEquals(1, beforeMatchOrderBook.sellLevels().size(), "매도 호가가 1개 있어야 함");
//			assertEquals(new BigDecimal("10"), beforeMatchOrderBook.sellLevels().get(0).quantity(), "매도 수량이 10이어야 함");
//
//			// When
//			// 매수 주문 추가 (매도 주문과 같은 가격으로 5개 수량)
//			Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE, account2);
//			orderBookService.received(buyOrder);
//
//			// Then
//			// 매수 주문 후 호가창 확인
//			OrderBookResponse afterMatchOrderBook = orderBookService.getBook();
//			assertEquals(1, afterMatchOrderBook.sellLevels().size(), "매도 호가가 여전히 1개 있어야 함");
//			assertEquals(new BigDecimal("5"), afterMatchOrderBook.sellLevels().get(0).quantity(), "매도 수량이 5로 줄어들어야 함");
//
//			// TradeHistoryService의 saveTradeHistory 메소드가 호출되었는지 확인
//			verify(tradeHistoryService, times(1)).saveTradeHistory(tradeHistoryCaptor.capture());
//			assertEquals(new BigDecimal("5"), tradeHistoryCaptor.getValue().quantity(), "거래 수량이 5여야 함");
//			assertEquals(new BigDecimal("50000"), tradeHistoryCaptor.getValue().price(), "거래 가격이 50000이어야 함");
//		}
//
//		@Test
//		@DisplayName("TC8.1.3 시장가 주문 테스트")
//		void testMarketOrder() {
//			// Given
//			OrderBookService orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService,
//					stockHoldingsService, accountService);
//			doNothing().when(tradeHistoryService).saveTradeHistory(any(TradeHistoryResponse.class));
//
//			// 지정가 매도 주문 2개 추가 (서로 다른 가격)
//			Order sellOrder1 = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE, account1);
//			Order sellOrder2 = createOrder(Type.SELL, new BigDecimal("51000"), new BigDecimal("5"), OrderStatus.ACTIVE, account1);
//			orderBookService.received(sellOrder1);
//			orderBookService.received(sellOrder2);
//
//			// When
//			// 시장가 매수 주문 추가 (가격은 0, 수량은 3)
//			Order marketBuyOrder = createOrder(Type.BUY, BigDecimal.ZERO, new BigDecimal("3"), OrderStatus.MARKET, account2);
//			orderBookService.received(marketBuyOrder);
//
//			// Then
//			// 호가창 확인
//			OrderBookResponse afterMarketOrderBook = orderBookService.getBook();
//			assertEquals(2, afterMarketOrderBook.sellLevels().size(), "매도 호가가 2개 있어야 함");
//			assertEquals(new BigDecimal("2"), afterMarketOrderBook.sellLevels().get(0).quantity(),
//					"첫 번째 매도 호가의 수량이 2로 줄어들어야 함");
//
//			// TradeHistoryService의 saveTradeHistory 메소드가 호출되었는지 확인
//			verify(tradeHistoryService, times(1)).saveTradeHistory(any(TradeHistoryResponse.class));
//		}

		@Test
		@DisplayName("TC8.1.4 시장가 주문 체결 불가 테스트")
		void testMarketOrderNoMatch() {
			// Given
			OrderBookService orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService,
					stockHoldingsService, accountService);

			// 시장가 매수 주문 (매도 호가가 없음)
			Order marketBuyOrder = createOrder(Type.BUY, BigDecimal.ZERO, new BigDecimal("5"), OrderStatus.MARKET, account1);

			// When & Then
			// 예외가 발생해야 함
			assertThrows(MatchingException.class, () -> orderBookService.received(marketBuyOrder),
					"매칭되는 매도 주문이 없을 때 MatchingException이 발생해야 함");

			// 시장가 매도 주문 (매수 호가가 없음)
			Order marketSellOrder = createOrder(Type.SELL, BigDecimal.ZERO, new BigDecimal("5"), OrderStatus.MARKET, account1);

			// 예외가 발생해야 함
			assertThrows(MatchingException.class, () -> orderBookService.received(marketSellOrder),
					"매칭되는 매수 주문이 없을 때 MatchingException이 발생해야 함");
		}

//		@Test
//		@DisplayName("TC8.1.5 부분 체결 테스트")
//		void testPartialOrderExecution() {
//			// Given
//			OrderBookService orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService,
//					stockHoldingsService, accountService);
//			doNothing().when(tradeHistoryService).saveTradeHistory(any(TradeHistoryResponse.class));
//
//			// 서로 다른 가격의 매도 주문 2개 추가
//			Order sellOrder1 = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE, account1);
//			Order sellOrder2 = createOrder(Type.SELL, new BigDecimal("50500"), new BigDecimal("10"),
//					OrderStatus.ACTIVE, account1);
//			orderBookService.received(sellOrder1);
//			orderBookService.received(sellOrder2);
//
//			// When
//			// 매수 주문 추가 (매도 주문보다 높은 가격, 매도 주문 총량보다 적은 수량)
//			Order buyOrder = createOrder(Type.BUY, new BigDecimal("51000"), new BigDecimal("7"), OrderStatus.ACTIVE, account2);
//			orderBookService.received(buyOrder);
//
//			// Then
//			// 호가창 확인
//			OrderBookResponse afterExecutionOrderBook = orderBookService.getBook();
//
//			// 매도 호가 확인
//			List<PriceLevelDto> sellLevels = afterExecutionOrderBook.sellLevels();
//			assertEquals(1, sellLevels.size(), "매도 호가가 1개만 남아 있어야 함");
//			assertEquals(new BigDecimal("50500"), sellLevels.get(0).price(), "남은 매도 호가는 높은 가격의 호가여야 함");
//			assertEquals(new BigDecimal("8"), sellLevels.get(0).quantity(), "남은 매도 수량이 8이어야 함 (원래 10개에서 2개 체결)");
//
//			// 매수 호가 확인 - 모두 체결되어 없어야 함
//			List<PriceLevelDto> buyLevels = afterExecutionOrderBook.buyLevels();
//			assertEquals(0, buyLevels.size(), "매수 호가가 없어야 함 (모두 체결됨)");
//
//			// TradeHistoryService saveTradeHistory 호출 확인
//			verify(tradeHistoryService, times(2)).saveTradeHistory(any(TradeHistoryResponse.class));
//		}

		@Test
		@DisplayName("TC8.1.6 호가 데이터 길이 검증")
		void testOrderBookLengthValidation() {
			// Given
			// OrderBookService orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService);

			// 초기 호가창 상태 확인
			OrderBookResponse initialOrderBook = orderBookService.getBook();
			assertNotNull(initialOrderBook.sellLevels(), "매도 호가 리스트는 null이 아니어야 함");
			assertNotNull(initialOrderBook.buyLevels(), "매수 호가 리스트는 null이 아니어야 함");

			// When
			// 다수의 매도 주문 생성 (10개 이상)
			for (int i = 0; i < 15; i++) {
				BigDecimal price = new BigDecimal("50000").add(new BigDecimal(i * 100));
				Order sellOrder = createOrder(Type.SELL, price, new BigDecimal("1"), OrderStatus.ACTIVE, account1);
				orderBookService.received(sellOrder);
			}

			// 다수의 매수 주문 생성 (10개 이상)
			for (int i = 0; i < 15; i++) {
				BigDecimal price = new BigDecimal("49000").subtract(new BigDecimal(i * 100));
				Order buyOrder = createOrder(Type.BUY, price, new BigDecimal("1"), OrderStatus.ACTIVE, account2);
				orderBookService.received(buyOrder);
			}

			// Then
			// 업데이트된 호가창 확인
			OrderBookResponse updatedOrderBook = orderBookService.getBook();

			// 최대 10개의 호가만 표시되는지 확인
			assertThat(updatedOrderBook.sellLevels().size()).isLessThanOrEqualTo(10);
			assertThat(updatedOrderBook.buyLevels().size()).isLessThanOrEqualTo(10);
		}

	}

	private Order createOrder(Long id, Type type, BigDecimal price, BigDecimal quantity, Long timestamp,
			OrderStatus status, Account account) {
		return Order.builder()
				.id(id)
				.companyCode("005930")
				.type(type)
				.totalQuantity(quantity)
				.remainingQuantity(quantity)
				.status(OrderStatus.ACTIVE)
				.price(price)
				.timestamp(timestamp)
				.status(status)
				.account(account)
				.build();
	}
}

