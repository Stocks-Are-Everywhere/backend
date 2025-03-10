package org.scoula.backend.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.scoula.backend.global.jwt.JwtUtil;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.service.exception.MatchingException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@DisplayName("OrderBookService 테스트")
@ActiveProfiles("test")
class OrderBookServiceTest {

	private OrderBookService orderBookService;

	@Mock
	private TradeHistoryService tradeHistoryService;

	@MockitoBean
	private JwtUtil jwtUtil;

	private final String COMPANY_CODE = "005930";

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		orderBookService = new OrderBookService(COMPANY_CODE, tradeHistoryService);
	}

	@Test
	@DisplayName("TC3.1.1 지정가 매수 주문 추가")
	@Transactional
	void testReceiveLimitBuyOrder() throws MatchingException {
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
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
		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
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
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
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

	@Test
	@DisplayName("TC3.1.3 시장가 매수 주문 처리")
	@Transactional
	void testMarketBuyOrder() throws MatchingException {
		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
		orderBookService.received(sellOrder);

		Order marketBuyOrder = createOrder(Type.BUY, BigDecimal.ZERO, new BigDecimal("10"), OrderStatus.MARKET);
		orderBookService.received(marketBuyOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertTrue(response.buyLevels().isEmpty());
		assertTrue(response.sellLevels().isEmpty());

		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
	}

	@Test
	@DisplayName("TC3.2.3 시장가 매도 주문 처리")
	@Transactional
	void testMarketSellOrder() throws MatchingException {
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
		orderBookService.received(buyOrder);

		Order marketSellOrder = createOrder(Type.SELL, BigDecimal.ZERO, new BigDecimal("10"), OrderStatus.MARKET);
		orderBookService.received(marketSellOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertTrue(response.buyLevels().isEmpty());
		assertTrue(response.sellLevels().isEmpty());

		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
	}

	@Test
	@DisplayName("TC3.1.2 & TC3.2.2 부분 체결")
	@Transactional
	void testPartialExecution() throws MatchingException {
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE);

		orderBookService.received(buyOrder);
		orderBookService.received(sellOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertFalse(response.buyLevels().isEmpty());
		assertTrue(response.sellLevels().isEmpty());
		assertEquals(new BigDecimal("5"), response.buyLevels().get(0).quantity());

		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
	}

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

	@Test
	@DisplayName("TC20.1.2 트랜잭션 ACID 속성 - 일관성 테스트")
	@Transactional
	void testTransactionConsistency() throws MatchingException {
		Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);

		orderBookService.received(buyOrder);
		orderBookService.received(sellOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertTrue(response.buyLevels().isEmpty());
		assertTrue(response.sellLevels().isEmpty());
		// 주문이 완전히 체결되어 주문장이 비어있어야 함
	}

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

	private Order createOrder(Type type, BigDecimal price, BigDecimal quantity, OrderStatus status) {
		return Order.builder()
			.companyCode(COMPANY_CODE)
			.type(type)
			.totalQuantity(quantity)
			.remainingQuantity(quantity)
			.status(status)
			.price(price)
			.timestamp(LocalDateTime.now())
			.build();
	}

	@Test
	@DisplayName("TC3.3 주문 불균형 상황 테스트")
	@Transactional
	void testOrderImbalance() throws MatchingException {
		// 다수의 매수 주문 생성
		for (int i = 0; i < 5; i++) {
			Order buyOrder = createOrder(Type.BUY, new BigDecimal("50000"), new BigDecimal("10"), OrderStatus.ACTIVE);
			orderBookService.received(buyOrder);
		}

		// 소수의 매도 주문 생성
		Order sellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("5"), OrderStatus.ACTIVE);
		orderBookService.received(sellOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertFalse(response.buyLevels().isEmpty());
		assertTrue(response.sellLevels().isEmpty());
		assertEquals(new BigDecimal("45"), response.buyLevels().get(0).quantity());

		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
	}

	@Test
	@DisplayName("TC3.4 대량 주문 처리 테스트")
	@Transactional
	void testLargeOrderProcessing() throws MatchingException {
		BigDecimal largeQuantity = new BigDecimal("1000000");
		Order largeBuyOrder = createOrder(Type.BUY, new BigDecimal("50000"), largeQuantity, OrderStatus.ACTIVE);
		orderBookService.received(largeBuyOrder);

		Order smallSellOrder = createOrder(Type.SELL, new BigDecimal("50000"), new BigDecimal("100"),
			OrderStatus.ACTIVE);
		orderBookService.received(smallSellOrder);

		OrderBookResponse response = orderBookService.getBook();
		assertFalse(response.buyLevels().isEmpty());
		assertTrue(response.sellLevels().isEmpty());
		assertEquals(largeQuantity.subtract(new BigDecimal("100")), response.buyLevels().get(0).quantity());

		verify(tradeHistoryService, times(1)).saveTradeHistory(any());
	}

	@Test
	@DisplayName("매수 주문의 경우 높은 가격의 주문부터 체결된다.")
	void buyOrderHigherPricePriorityMatching() throws MatchingException {
		LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
		Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(2000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(0), new BigDecimal(10), createdAt,
			OrderStatus.MARKET);

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
		LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
		Order sellOrder1 = createOrder(1L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order sellOrder2 = createOrder(2L, Type.SELL, new BigDecimal(2000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order buyOrder = createOrder(3L, Type.BUY, new BigDecimal(0), new BigDecimal(10), createdAt,
			OrderStatus.MARKET);

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
		LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
		Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt.plusSeconds(1),
			OrderStatus.ACTIVE);
		Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.MARKET);
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
		LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
		Order sellOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt.plusSeconds(1),
			OrderStatus.ACTIVE);
		Order sellOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order buyOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.MARKET);

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
		LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
		Order buyOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order buyOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(11), createdAt,
			OrderStatus.ACTIVE);
		Order sellOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.MARKET);

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
		LocalDateTime createdAt = LocalDateTime.of(2025, 3, 3, 0, 0);
		Order sellOrder1 = createOrder(1L, Type.BUY, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.ACTIVE);
		Order sellOrder2 = createOrder(2L, Type.BUY, new BigDecimal(1000), new BigDecimal(11), createdAt,
			OrderStatus.ACTIVE);
		Order buyOrder = createOrder(3L, Type.SELL, new BigDecimal(1000), new BigDecimal(10), createdAt,
			OrderStatus.MARKET);

		orderBookService.received(sellOrder1);
		orderBookService.received(sellOrder2);
		orderBookService.received(buyOrder);

		assertThat(sellOrder1.getRemainingQuantity()).isEqualTo(new BigDecimal(10));
		assertThat(sellOrder2.getRemainingQuantity()).isEqualTo(new BigDecimal(1));
		assertThat(buyOrder.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
	}

	private Order createOrder(Long id, Type type, BigDecimal price, BigDecimal quantity, LocalDateTime timestamp,
		OrderStatus status) {
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
			.build();
	}
}

