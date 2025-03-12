package org.scoula.backend.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;

// Slf4j 대신 직접 로거 선언
public class CustomTest {

	@Mock
	private TradeHistoryService tradeHistoryService;

	@Mock
	private StockHoldingsService stockHoldingsService;

	@Mock
	private AccountService accountService;

	private static final String COMPANY_CODE = "TEST001";
	private static final int THREAD_COUNT = 50;
	private static final int ORDER_COUNT = 10000;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		// 모의 서비스 설정
		doNothing().when(tradeHistoryService).saveTradeHistory(any());
		doNothing().when(stockHoldingsService).updateHoldingsAfterTrade(any(), any(), any(), any(), any());
		doNothing().when(accountService).updateAccountAfterTrade(any(), any(), any(), any());
	}

	// 지그재그 패턴의 가격을 가진 매수 주문 생성
	private List<Order> createZigzagPriceBuyOrders(int count) {
		List<Order> orders = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Order order = mock(Order.class);
			when(order.getId()).thenReturn(Long.valueOf(i));
			when(order.getType()).thenReturn(Type.BUY);

			// 지그재그 패턴의 가격 설정 (90, 110, 70, 130, ...)
			BigDecimal price = new BigDecimal(100 + (i % 5) * 10);
			when(order.getPrice()).thenReturn(price);

			when(order.getTotalQuantity()).thenReturn(new BigDecimal("10"));
			when(order.getRemainingQuantity()).thenReturn(new BigDecimal("10"));
			when(order.getStatus()).thenReturn(OrderStatus.ACTIVE);
			when(order.getCompanyCode()).thenReturn(COMPANY_CODE);
			when(order.isSellType()).thenReturn(false);
			when(order.getTimestamp()).thenReturn(System.currentTimeMillis() + i);

			Account account = mock(Account.class);
			Member member = mock(Member.class);
			when(member.getId()).thenReturn(Long.valueOf(i));
			when(account.getMember()).thenReturn(member);
			when(order.getAccount()).thenReturn(account);

			// 주문 수량 감소 메서드 모킹
			doAnswer(invocation -> {
				BigDecimal quantity = invocation.getArgument(0);
				BigDecimal newRemaining = new BigDecimal("10").subtract(quantity);
				when(order.getRemainingQuantity()).thenReturn(newRemaining);
				return null;
			}).when(order).decreaseRemainingQuantity(any(BigDecimal.class));

			orders.add(order);
		}
		return orders;
	}

	// 지그재그 패턴의 가격을 가진 매도 주문 생성
	private List<Order> createZigzagPriceSellOrders(int count) {
		List<Order> orders = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Order order = mock(Order.class);
			when(order.getId()).thenReturn(Long.valueOf(count + i));
			when(order.getType()).thenReturn(Type.SELL);

			// 지그재그 패턴의 가격 설정 (110, 90, 130, 70, ...)
			BigDecimal price = new BigDecimal(100 - (i % 5) * 10);
			when(order.getPrice()).thenReturn(price);

			when(order.getTotalQuantity()).thenReturn(new BigDecimal("10"));
			when(order.getRemainingQuantity()).thenReturn(new BigDecimal("10"));
			when(order.getStatus()).thenReturn(OrderStatus.ACTIVE);
			when(order.getCompanyCode()).thenReturn(COMPANY_CODE);
			when(order.isSellType()).thenReturn(true);
			when(order.getTimestamp()).thenReturn(System.currentTimeMillis() + i);

			Account account = mock(Account.class);
			Member member = mock(Member.class);
			when(member.getId()).thenReturn(Long.valueOf(count + i));
			when(account.getMember()).thenReturn(member);
			when(order.getAccount()).thenReturn(account);

			// 주문 수량 감소 메서드 모킹
			doAnswer(invocation -> {
				BigDecimal quantity = invocation.getArgument(0);
				BigDecimal newRemaining = new BigDecimal("10").subtract(quantity);
				when(order.getRemainingQuantity()).thenReturn(newRemaining);
				return null;
			}).when(order).decreaseRemainingQuantity(any(BigDecimal.class));

			orders.add(order);
		}
		return orders;
	}

	// 시나리오 1: ConcurrentMap + Process Synchronized + PriorityQueue (AtomicReference On/Off)
	@Test
	public void testSynchronizedProcessOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		SynchronizedProcessOrderBookService service = new SynchronizedProcessOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println("===== 시나리오 1: SynchronizedProcessOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	// 시나리오 2: ConcurrentMap + Process Synchronized + MatchOrder Synchronized + PriorityQueue (AtomicReference On/Off)
	@Test
	public void testSynchronizedProcessMatchOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		SynchronizedProcessMatchOrderBookService service = new SynchronizedProcessMatchOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println("===== 시나리오 2: SynchronizedProcessMatchOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	// 시나리오 3: ConcurrentMap + Process Synchronized + MatchOrder ReentrantLock + PriorityQueue (AtomicReference On/Off)
	@Test
	public void testSynchronizedProcessLockMatchOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		SynchronizedProcessLockMatchOrderBookService service = new SynchronizedProcessLockMatchOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 3: SynchronizedProcessLockMatchOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	// 시나리오 4: ConcurrentMap + Process ReentrantLock + PriorityQueue (AtomicReference On/Off)
	@Test
	public void testLockProcessOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		LockProcessOrderBookService service = new LockProcessOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println("===== 시나리오 4: LockProcessOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	// 시나리오 5: ConcurrentMap + Process ReentrantLock + MatchOrder Synchronized + PriorityQueue (AtomicReference On/Off)
	@Test
	public void testLockProcessSynchronizedMatchOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		LockProcessSynchronizedMatchOrderBookService service = new LockProcessSynchronizedMatchOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 5: LockProcessSynchronizedMatchOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	// 시나리오 6: ConcurrentMap + Process ReentrantLock + MatchOrder ReentrantLock + PriorityQueue (AtomicReference On/Off)
	@Test
	public void testLockProcessLockMatchOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		LockProcessLockMatchOrderBookService service = new LockProcessLockMatchOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println("===== 시나리오 6: LockProcessLockMatchOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	@Test
	public void testSynchronizedProcessConcurrentQueueOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		SynchronizedProcessConcurrentQueueOrderBookService service = new SynchronizedProcessConcurrentQueueOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 7: SynchronizedProcessConcurrentQueueOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	/**
	 * 시나리오 8: ConcurrentMap + Process Synchronized + MatchOrder Synchronized + ConcurrentLinkedQueue (AtomicReference Off)
	 */
	@Test
	public void testSynchronizedProcessMatchConcurrentQueueOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		SynchronizedProcessMatchConcurrentQueueOrderBookService service = new SynchronizedProcessMatchConcurrentQueueOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 8: SynchronizedProcessMatchConcurrentQueueOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	/**
	 * 시나리오 9: ConcurrentMap + Process Synchronized + MatchOrder ReentrantLock + ConcurrentLinkedQueue (AtomicReference Off)
	 */
	@Test
	public void testSynchronizedProcessLockMatchConcurrentQueueOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		SynchronizedProcessLockMatchConcurrentQueueOrderBookService service = new SynchronizedProcessLockMatchConcurrentQueueOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 9: SynchronizedProcessLockMatchConcurrentQueueOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	/**
	 * 시나리오 10: ConcurrentMap + Process ReentrantLock + ConcurrentLinkedQueue (AtomicReference Off)
	 */
	@Test
	public void testLockProcessConcurrentQueueOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		LockProcessConcurrentQueueOrderBookService service = new LockProcessConcurrentQueueOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 10: LockProcessConcurrentQueueOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	/**
	 * 시나리오 11: ConcurrentMap + Process ReentrantLock + MatchOrder Synchronized + ConcurrentLinkedQueue (AtomicReference Off)
	 */
	@Test
	public void testLockProcessSynchronizedMatchConcurrentQueueOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		LockProcessSynchronizedMatchConcurrentQueueOrderBookService service = new LockProcessSynchronizedMatchConcurrentQueueOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 11: LockProcessSynchronizedMatchConcurrentQueueOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

	/**
	 * 시나리오 12: ConcurrentMap + Process ReentrantLock + MatchOrder ReentrantLock + ConcurrentLinkedQueue (AtomicReference Off)
	 */
	@Test
	public void testLockProcessLockMatchConcurrentQueueOrderBookService() throws InterruptedException {
		// 테스트 대상 서비스 생성
		LockProcessLockMatchConcurrentQueueOrderBookService service = new LockProcessLockMatchConcurrentQueueOrderBookService(
			COMPANY_CODE, tradeHistoryService, stockHoldingsService, accountService);

		// 지그재그 패턴의 가격을 가진 주문 생성
		List<Order> buyOrders = createZigzagPriceBuyOrders(ORDER_COUNT);
		List<Order> sellOrders = createZigzagPriceSellOrders(ORDER_COUNT);

		// 성능 측정 변수
		long startTime = System.currentTimeMillis();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// 멀티스레드 테스트 설정
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch latch = new CountDownLatch(buyOrders.size() + sellOrders.size());

		// 주문 제출
		for (int i = 0; i < Math.max(buyOrders.size(), sellOrders.size()); i++) {
			final int index = i;

			// 매수 주문 제출
			if (i < buyOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(buyOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			// 매도 주문 제출
			if (i < sellOrders.size()) {
				executorService.submit(() -> {
					try {
						service.received(sellOrders.get(index));
						successCount.incrementAndGet();
					} catch (Exception e) {
						failCount.incrementAndGet();
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}
		}

		// 모든 작업 완료 대기
		boolean completed = latch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// 테스트 결과 측정
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 결과 검증 및 출력
		assertTrue(completed, "테스트가 시간 내에 완료되지 않았습니다.");
		assertEquals(0, failCount.get(), "일부 주문 처리에 실패했습니다.");

		// 주문 처리 결과 검증
		int fullyMatchedOrders = 0;
		for (Order order : buyOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}
		for (Order order : sellOrders) {
			if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				fullyMatchedOrders++;
			}
		}

		System.out.println(
			"===== 시나리오 12: LockProcessLockMatchConcurrentQueueOrderBookService (AtomicReference Off) 테스트 결과 =====");
		System.out.println("총 주문 수: " + (buyOrders.size() + sellOrders.size()));
		System.out.println("성공 처리 수: " + successCount.get());
		System.out.println("실패 처리 수: " + failCount.get());
		System.out.println("완전 체결된 주문 수: " + fullyMatchedOrders);
		System.out.println("처리 시간: " + duration + "ms");

		// 추가 검증: 주문장 상태 확인
		OrderBookResponse book = service.getBook();
		System.out.println("남은 매수 호가 수: " + book.buyLevels().size());
		System.out.println("남은 매도 호가 수: " + book.sellLevels().size());
	}

}
