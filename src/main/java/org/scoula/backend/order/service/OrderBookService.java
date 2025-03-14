package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.PriceLevelDto;
import org.scoula.backend.order.service.batch.OrderPersistentQueue;
import org.scoula.backend.order.service.exception.MatchingException;
import org.springframework.beans.factory.annotation.Qualifier;

import lombok.extern.slf4j.Slf4j;

/**
 * 개별 종목의 주문장
 * - 동시성 제어
 * - 비동기 처리 최적화
 */
@Slf4j
public class OrderBookService {
	// 종목 번호
	private final String companyCode;

	// 매도 주문: 낮은 가격 우선
	private final ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<Order>> sellOrders
			= new ConcurrentSkipListMap<>();

	// 매수 주문: 높은 가격 우선
	private final ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<Order>> buyOrders
			= new ConcurrentSkipListMap<>(Collections.reverseOrder());

	// 리포지토리 의존성
	private final OrderRepository orderRepository;

	// 비즈니스 서비스 의존성
	private final TradeHistoryService tradeHistoryService;
	private final StockHoldingsService stockHoldingsService;
	private final AccountService accountService;

	// 스레드풀 의존성
	@Qualifier("dbOperationsExecutor")
	private final Executor dbOperationsExecutor;

	// 디스크 큐 의존성
	private final OrderPersistentQueue orderQueue;

	// 동시성 제어
	private final StampedLock matchingLock = new StampedLock();

	/**
	 * 생성자
	 */
	public OrderBookService(final String companyCode,
			final TradeHistoryService tradeHistoryService,
			final StockHoldingsService stockHoldingsService,
			final AccountService accountService,
			final OrderRepository orderRepository,
			final Executor dbOperationsExecutor,
			final OrderPersistentQueue orderQueue) {
		this.companyCode = companyCode;
		this.tradeHistoryService = tradeHistoryService;
		this.stockHoldingsService = stockHoldingsService;
		this.accountService = accountService;
		this.orderRepository = orderRepository;
		this.dbOperationsExecutor = dbOperationsExecutor;
		this.orderQueue = orderQueue;
	}

	/**
	 * 주문 접수 및 처리
	 */
	public void received(final Order order) throws MatchingException {
		log.info("주문 접수 - 종목: {}, 주문ID: {}, 타입: {}, 가격: {}, 수량: {}",
				companyCode, order.getId(), order.getType(), order.getPrice(), order.getTotalQuantity());

		if (order.getStatus() == OrderStatus.MARKET) {
			processMarketOrder(order);
		} else {
			processLimitOrder(order);
		}

		log.info("주문 처리 완료 - 주문ID: {}, 잔여수량: {}",
				order.getId(), order.getRemainingQuantity());
	}

	/**
	 * 시장가 주문 처리
	 */
	private void processMarketOrder(final Order order) throws MatchingException {
		if (order.getType() == Type.BUY) {
			matchMarketBuyOrder(order);
		} else {
			matchMarketSellOrder(order);
		}
	}

	/**
	 * 지정가 주문 처리
	 */
	private void processLimitOrder(final Order order) {
		if (order.getType() == Type.BUY) {
			matchBuyOrder(order);
		} else {
			matchSellOrder(order);
		}
	}

	/**
	 * 지정가 매도 주문 처리 - 낙관적 락 패턴 적용
	 */
	private void matchSellOrder(final Order sellOrder) {
		// 낙관적 읽기 락 획득
		long stamp = matchingLock.tryOptimisticRead();

		try {
			while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
				// 매도가보다 높거나 같은 매수 주문 찾기
				Map.Entry<BigDecimal, ConcurrentSkipListSet<Order>> bestBuy = buyOrders.firstEntry();

				// 매칭 가능한 매수 주문이 없으면 주문장에 추가
				if (bestBuy == null || bestBuy.getKey().compareTo(sellOrder.getPrice()) < 0) {
					// 낙관적 락 유효성 확인
					if (!matchingLock.validate(stamp)) {
						// 낙관적 락이 무효화된 경우 쓰기 락으로 전환
						stamp = matchingLock.writeLock();
						try {
							addToOrderBook(sellOrders, sellOrder);
							break;
						} finally {
							matchingLock.unlock(stamp);
							// 락 해제 후 메서드 종료
							return;
						}
					} else {
						// 낙관적 락이 유효한 경우 그대로 진행
						addToOrderBook(sellOrders, sellOrder);
						break;
					}
				}

				// 주문 매칭 처리 - 이 부분에서 락 충돌 가능성이 있음
				if (!matchOrders(bestBuy.getValue(), sellOrder)) {
					// 매칭 처리 중 락 유효성 검증 실패
					// 읽기 락을 다시 획득하고 반복문 다시 시작
					stamp = matchingLock.tryOptimisticRead();
					continue;
				}

				// 매수 집합이 비었으면 제거
				if (bestBuy.getValue().isEmpty()) {
					buyOrders.remove(bestBuy.getKey());
				}

				// 낙관적 락 유효성 재확인
				if (!matchingLock.validate(stamp)) {
					stamp = matchingLock.tryOptimisticRead();
				}
			}
		} catch (MatchingException e) {
			// 예외 발생 시 쓰기 락 획득 시도
			stamp = matchingLock.writeLock();
			try {
				// 매칭 실패 원인 로깅
				log.error("매도 주문 매칭 중 오류 발생 - 주문ID: {}, 오류: {}", sellOrder.getId(), e.getMessage(), e);
				// 남은 수량이 있으면 주문장에 추가
				if (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
					addToOrderBook(sellOrders, sellOrder);
				}
			} finally {
				matchingLock.unlock(stamp);
			}
			throw e;
		}
	}

	/**
	 * 시장가 매도 주문 처리
	 */
	private void matchMarketSellOrder(final Order sellOrder) throws MatchingException {
		// 쓰기 락 획득 - 시장가 주문은 즉시 체결되어야 함
		long stamp = matchingLock.writeLock();

		try {
			while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
				// 매수 주문 찾기
				Map.Entry<BigDecimal, ConcurrentSkipListSet<Order>> bestBuy = buyOrders.firstEntry();
				if (bestBuy == null) {
					throw new MatchingException("시장가 매도 주문 체결 불가 : " + sellOrder.getRemainingQuantity());
				}

				// 주문 매칭 처리
				matchOrdersWithLock(bestBuy.getValue(), sellOrder);

				// 매수 집합이 비었으면 제거
				if (bestBuy.getValue().isEmpty()) {
					buyOrders.remove(bestBuy.getKey());
				}
			}
		} finally {
			matchingLock.unlock(stamp);
		}
		log.debug("시장가 매도 체결 완료");
	}

	/**
	 * 지정가 매수 주문 처리
	 */
	private void matchBuyOrder(final Order buyOrder) {
		// 낙관적 읽기 락 획득
		long stamp = matchingLock.tryOptimisticRead();

		try {
			while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
				// 매수가보다 낮거나 같은 매도 주문 찾기
				Map.Entry<BigDecimal, ConcurrentSkipListSet<Order>> bestSell = sellOrders.firstEntry();

				// 매칭 가능한 매도 주문이 없으면 주문장에 추가
				if (bestSell == null || bestSell.getKey().compareTo(buyOrder.getPrice()) > 0) {
					// 낙관적 락 유효성 확인
					if (!matchingLock.validate(stamp)) {
						// 낙관적 락이 무효화된 경우 쓰기 락으로 전환
						stamp = matchingLock.writeLock();
						try {
							addToOrderBook(buyOrders, buyOrder);
							break;
						} finally {
							matchingLock.unlock(stamp);
							// 락 해제 후 메서드 종료
							return;
						}
					} else {
						// 낙관적 락이 유효한 경우 그대로 진행
						addToOrderBook(buyOrders, buyOrder);
						break;
					}
				}

				// 주문 매칭 처리 - 이 부분에서 락 충돌 가능성이 있음
				if (!matchOrders(bestSell.getValue(), buyOrder)) {
					// 매칭 처리 중 락 유효성 검증 실패
					// 읽기 락을 다시 획득하고 반복문 다시 시작
					stamp = matchingLock.tryOptimisticRead();
					continue;
				}

				// 매도 집합이 비었으면 제거
				if (bestSell.getValue().isEmpty()) {
					sellOrders.remove(bestSell.getKey());
				}

				// 낙관적 락 유효성 재확인
				if (!matchingLock.validate(stamp)) {
					stamp = matchingLock.tryOptimisticRead();
				}
			}
		} catch (MatchingException e) {
			// 예외 발생 시 쓰기 락 획득 시도
			stamp = matchingLock.writeLock();
			try {
				// 매칭 실패 원인 로깅
				log.error("매수 주문 매칭 중 오류 발생 - 주문ID: {}, 오류: {}", buyOrder.getId(), e.getMessage(), e);
				// 남은 수량이 있으면 주문장에 추가
				if (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
					addToOrderBook(buyOrders, buyOrder);
				}
			} finally {
				matchingLock.unlock(stamp);
			}
			throw e;
		}
	}

	/**
	 * 시장가 매수 주문 처리
	 */
	private void matchMarketBuyOrder(final Order buyOrder) throws MatchingException {
		// 쓰기 락 획득 - 시장가 주문은 즉시 체결되어야 함
		long stamp = matchingLock.writeLock();

		try {
			while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
				// 매도 주문 찾기
				Map.Entry<BigDecimal, ConcurrentSkipListSet<Order>> bestSell = sellOrders.firstEntry();
				if (bestSell == null) {
					throw new MatchingException("시장가 매수 주문 체결 불가 : " + buyOrder.getRemainingQuantity());
				}

				// 주문 매칭 처리
				matchOrdersWithLock(bestSell.getValue(), buyOrder);

				// 매도 집합이 비었으면 제거
				if (bestSell.getValue().isEmpty()) {
					sellOrders.remove(bestSell.getKey());
				}
			}
		} finally {
			matchingLock.unlock(stamp);
		}
		log.debug("시장가 매수 체결 완료");
	}

	/**
	 * 낙관적 락을 사용한 주문 매칭 처리
	 * @return 매칭 처리 성공 여부 (락 유효성 검증 결과)
	 */
	private boolean matchOrders(final ConcurrentSkipListSet<Order> existingOrders, final Order incomingOrder) {
		// 처리 중에 제외된 주문들을 임시 저장
		final Queue<Order> skippedOrders = new PriorityQueue<>(
				Comparator.comparing(Order::getTimestamp)
						.thenComparing(Order::getTotalQuantity, Comparator.reverseOrder())
		);

		// 변경된 주문을 추적하기 위한 Set
		final Set<Order> orderToUpdate = new HashSet<>();

		try {
			// 주문 매칭 로직 실행
			while (!existingOrders.isEmpty() && incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
				// existingOrders에서 첫 번째 주문 조회 (아직 제거하지 않음)
				final Order existingOrder = existingOrders.first();

				// 동일 유저 주문인 경우 건너뛰기
				if (incomingOrder.getAccount().getMember().equals(existingOrder.getAccount().getMember())) {
					// 처리한 주문은 집합에서 제거하고 임시 집합에 추가
					existingOrders.remove(existingOrder);
					skippedOrders.offer(existingOrder);
					continue;
				}

				final BigDecimal matchedQuantity = incomingOrder.getRemainingQuantity()
						.min(existingOrder.getRemainingQuantity());
				final BigDecimal matchPrice = existingOrder.getPrice(); // 체결 가격은 항상 기존 주문 가격

				// 1. 매칭 전 값들 기록
				final BigDecimal originalIncomingRemaining = incomingOrder.getRemainingQuantity();
				final BigDecimal originalExistingRemaining = existingOrder.getRemainingQuantity();
				final OrderStatus originalIncomingStatus = incomingOrder.getStatus();
				final OrderStatus originalExistingStatus = existingOrder.getStatus();

				// 2. 주문 수량 업데이트
				incomingOrder.processMatch(matchedQuantity);
				existingOrder.processMatch(matchedQuantity);

				// 3. 상태나 남은 수량이 변경된 경우 업데이트할 주문 목록에 추가
				if (originalIncomingStatus != incomingOrder.getStatus()
						|| originalIncomingRemaining.compareTo(incomingOrder.getRemainingQuantity()) != 0) {
					orderToUpdate.add(incomingOrder);
				}
				if (originalExistingStatus != existingOrder.getStatus()
						|| originalExistingRemaining.compareTo(existingOrder.getRemainingQuantity()) != 0) {
					orderToUpdate.add(existingOrder);
				}

				// 4. 매수자/매도자 결정
				Order buyOrder, sellOrder;
				if (incomingOrder.isSellType()) {
					buyOrder = existingOrder;
					sellOrder = incomingOrder;
				} else {
					buyOrder = incomingOrder;
					sellOrder = existingOrder;
				}

				// 5. 거래 처리
				processTradeMatch(buyOrder, sellOrder, matchPrice, matchedQuantity);

				// 6. 처리한 주문은 집합에서 제거
				existingOrders.remove(existingOrder);

				// 7. 완전 체결되지 않은 주문은 다시 집합에 추가
				if (!existingOrder.isCompletelyFilled()) {
					existingOrders.add(existingOrder);
				}
			}

			// 8. 임시 큐에 저장했던 건너뛴 주문들을 다시 원래 집합에 추가
			while (!skippedOrders.isEmpty()) {
				existingOrders.add(skippedOrders.poll());
			}

			// 9. 인커밍 주문의 남은 수량 처리
			if (!incomingOrder.isCompletelyFilled()) {
				// 주문 타입에 맞는 주문장에 남은 수량이 있는 주문 추가
				if (incomingOrder.getType() == Type.BUY) {
					addToOrderBook(buyOrders, incomingOrder);
				} else {
					addToOrderBook(sellOrders, incomingOrder);
				}
			}

			// 10. 변경된 주문 DB 업데이트
			updateOrdersInDatabase(orderToUpdate);

			// 매칭 성공
			return true;
		} catch (MatchingException e) {
			log.error("주문 매칭 중 오류: {}", e.getMessage(), e);
			// 매칭 실패
			return false;
		}
	}

	/**
	 * 락을 이미 획득한 상태에서의 주문 매칭 처리
	 */
	private void matchOrdersWithLock(final ConcurrentSkipListSet<Order> existingOrders, final Order incomingOrder) {
		// 처리 중에 제외된 주문들을 임시 저장
		final Queue<Order> skippedOrders = new PriorityQueue<>(
				Comparator.comparing(Order::getTimestamp)
						.thenComparing(Order::getTotalQuantity, Comparator.reverseOrder())
		);

		// 변경된 주문을 추적하기 위한 Set
		final Set<Order> orderToUpdate = new HashSet<>();

		try {
			// 주문 매칭 로직 실행
			while (!existingOrders.isEmpty() && incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
				// existingOrders에서 첫 번째 주문 조회 (아직 제거하지 않음)
				final Order existingOrder = existingOrders.first();

				// 동일 유저 주문인 경우 건너뛰기
				if (incomingOrder.getAccount().getMember().equals(existingOrder.getAccount().getMember())) {
					// 처리한 주문은 집합에서 제거하고 임시 집합에 추가
					existingOrders.remove(existingOrder);
					skippedOrders.offer(existingOrder);
					continue;
				}

				final BigDecimal matchedQuantity = incomingOrder.getRemainingQuantity()
						.min(existingOrder.getRemainingQuantity());
				final BigDecimal matchPrice = existingOrder.getPrice(); // 체결 가격은 항상 기존 주문 가격

				// 1. 매칭 전 값들 기록
				final BigDecimal originalIncomingRemaining = incomingOrder.getRemainingQuantity();
				final BigDecimal originalExistingRemaining = existingOrder.getRemainingQuantity();
				final OrderStatus originalIncomingStatus = incomingOrder.getStatus();
				final OrderStatus originalExistingStatus = existingOrder.getStatus();

				// 2. 주문 수량 업데이트
				incomingOrder.processMatch(matchedQuantity);
				existingOrder.processMatch(matchedQuantity);

				// 3. 상태나 남은 수량이 변경된 경우 업데이트할 주문 목록에 추가
				if (originalIncomingStatus != incomingOrder.getStatus()
						|| originalIncomingRemaining.compareTo(incomingOrder.getRemainingQuantity()) != 0) {
					orderToUpdate.add(incomingOrder);
				}
				if (originalExistingStatus != existingOrder.getStatus()
						|| originalExistingRemaining.compareTo(existingOrder.getRemainingQuantity()) != 0) {
					orderToUpdate.add(existingOrder);
				}

				// 4. 매수자/매도자 결정
				Order buyOrder, sellOrder;
				if (incomingOrder.isSellType()) {
					buyOrder = existingOrder;
					sellOrder = incomingOrder;
				} else {
					buyOrder = incomingOrder;
					sellOrder = existingOrder;
				}

				// 5. 거래 처리
				processTradeMatch(buyOrder, sellOrder, matchPrice, matchedQuantity);

				// 6. 처리한 주문은 집합에서 제거
				existingOrders.remove(existingOrder);

				// 7. 완전 체결되지 않은 주문은 다시 집합에 추가
				if (!existingOrder.isCompletelyFilled()) {
					existingOrders.add(existingOrder);
				}
			}

			// 8. 임시 큐에 저장했던 건너뛴 주문들을 다시 원래 집합에 추가
			while (!skippedOrders.isEmpty()) {
				existingOrders.add(skippedOrders.poll());
			}

			// 9. 변경된 주문 DB 업데이트
			updateOrdersInDatabase(orderToUpdate);
		} catch (MatchingException e) {
			log.error("주문 매칭 중 오류: {}", e.getMessage(), e);
			throw e; // 락이 획득된 상태에서는 예외를 그대로 전파
		}
	}

	// 변경된 주문들을 DB에 비동기적으로 업데이트
	private void updateOrdersInDatabase(final Set<Order> orderToUpdate) {
		if (!orderToUpdate.isEmpty()) {
			// 스레드 안정성을 위한 복사
			final Set<Order> ordersToSave = new HashSet<>(orderToUpdate);

			CompletableFuture.runAsync(() -> {
				ordersToSave.forEach(order -> {
					try {
						final OrderStatus beforeStatus = order.getStatus();
						final BigDecimal beforeQuantity = order.getRemainingQuantity();

						// final Order savedOrder = orderRepository.save(order);

						// 디스크 큐를 통한 배치 처리 활용
						orderQueue.enqueueUpdate(order).join();

						log.info("주문 DB 업데이트 성공 - 주문ID: {}, 상태: {} -> {}, 남은 수량: {} -> {}",
								order.getId(), beforeStatus, order.getStatus(),
								beforeQuantity, order.getRemainingQuantity());
					} catch (MatchingException e) {
						log.error("주문 DB 업데이트 실패 - 주문ID: {}, 오류: {}",
								order.getId(), e.getMessage(), e);
					}
				});
			}, dbOperationsExecutor).orTimeout(5, TimeUnit.SECONDS); // 타임아웃 추가
		} else {
			log.warn("변경된 주문이 없음 - 매칭은 발생했지만 상태 변경이 없음");
		}
	}

	// 매수/매도 주문 체결 처리
	private void processTradeMatch(
			final Order buyOrder, final Order sellOrder, final BigDecimal price, final BigDecimal quantity) {
		final String companyCode = buyOrder.getCompanyCode();
		log.info("매수/매도 주문 체결 처리 - 매수ID: {}, 매도ID: {}, 가격: {}, 수량: {}, 종목: {}",
				buyOrder.getId(), sellOrder.getId(), price, quantity, companyCode);

		try {
			// 1. 거래 내역 저장
			final TradeHistoryResponse tradeHistory = TradeHistoryResponse.builder()
					.companyCode(companyCode)
					.buyOrderId(buyOrder.getId())
					.sellOrderId(sellOrder.getId())
					.quantity(quantity)
					.price(price)
					.tradeTime(Instant.now().getEpochSecond())
					.build();

			CompletableFuture.runAsync(() -> {
				try {
					tradeHistoryService.saveTradeHistory(tradeHistory);
				} catch (MatchingException e) {
					log.error("거래 내역 저장 실패: {}", e.getMessage(), e);
				}
			}, dbOperationsExecutor);

			// 2. 계좌 잔액 처리 (비동기)
			CompletableFuture.runAsync(() -> {
				try {
					accountService.updateAccountAfterTrade(Type.BUY, buyOrder.getAccount(), price, quantity);
					accountService.updateAccountAfterTrade(Type.SELL, sellOrder.getAccount(), price, quantity);
				} catch (MatchingException e) {
					log.error("계좌 잔액 업데이트 실패: {}", e.getMessage(), e);
				}
			}, dbOperationsExecutor);

			// 3. 보유 주식 처리
			CompletableFuture.runAsync(() -> {
				try {
					stockHoldingsService.updateHoldingsAfterTrade(
							Type.BUY, buyOrder.getAccount(), companyCode, price, quantity);
					stockHoldingsService.updateHoldingsAfterTrade(
							Type.SELL, sellOrder.getAccount(), companyCode, price, quantity);
				} catch (MatchingException e) {
					log.error("계좌 잔액 업데이트 실패: {}", e.getMessage(), e);
				}
			}, dbOperationsExecutor);
		} catch (MatchingException e) {
			log.error("거래 처리 중 오류: {}", e.getMessage(), e);
			// 재시도 메커니즘 구현 필요
		}
	}

	/**
	 * 주문장에 주문 추가
	 */
	private void addToOrderBook(
			final ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<Order>> orderBook,
			final Order order) {
		if (order.getPrice().compareTo(BigDecimal.ZERO) == 0) {
			log.warn("시장가 주문은 주문장에 추가할 수 없습니다: {}", order);
			return;
		}

		// 해당 가격대의 주문 집합 가져오기 (없으면 새로 생성)
		ConcurrentSkipListSet<Order> orderSet = orderBook.computeIfAbsent(
				order.getPrice(),
				k -> new ConcurrentSkipListSet<>(
						(o1, o2) -> {
							// 시간 우선, 수량 차선 정렬
							int timeCompare = o1.getTimestamp().compareTo(o2.getTimestamp());
							if (timeCompare != 0)
								return timeCompare;

							// 동일 시간이면 수량이 많은 주문 우선
							return o2.getTotalQuantity().compareTo(o1.getTotalQuantity());
						}
				)
		);

		// 주문 집합에 추가
		orderSet.add(order);
	}

	/**
	 * 종목별 주문장 스냅샷 생성
	 */
	public OrderSnapshotResponse getSnapshot() {
		return new OrderSnapshotResponse(companyCode, sellOrders, buyOrders);
	}

	/**
	 * 호가창 생성
	 */
	public OrderBookResponse getBook() {
		// 낙관적 읽기 락 획득
		long stamp = matchingLock.tryOptimisticRead();
		List<PriceLevelDto> sellLevels;
		List<PriceLevelDto> buyLevels;

		try {
			// 호가창 데이터 생성
			sellLevels = createAskLevels();
			buyLevels = createBidLevels();

			// 낙관적 락 유효성 확인
			if (!matchingLock.validate(stamp)) {
				// 유효하지 않으면 읽기 락 획득 후 다시 시도
				stamp = matchingLock.readLock();
				try {
					sellLevels = createAskLevels();
					buyLevels = createBidLevels();
				} finally {
					matchingLock.unlockRead(stamp);
				}
			}
		} catch (MatchingException e) {
			// 예외 발생 시 읽기 락 획득 후 다시 시도
			stamp = matchingLock.readLock();
			try {
				sellLevels = createAskLevels();
				buyLevels = createBidLevels();
			} finally {
				matchingLock.unlockRead(stamp);
			}
		}

		return OrderBookResponse.builder()
				.companyCode(companyCode)
				.sellLevels(sellLevels)
				.buyLevels(buyLevels)
				.build();
	}

	/**
	 * 매도 호가창 정보 생성
	 */
	private List<PriceLevelDto> createAskLevels() {
		return this.sellOrders.entrySet().stream()
				.limit(10)
				.map(entry -> new PriceLevelDto(
						entry.getKey(), calculateTotalQuantity(entry.getValue()), entry.getValue().size())
				).toList();
	}

	/**
	 * 매수 호가창 정보 생성
	 */
	private List<PriceLevelDto> createBidLevels() {
		return this.buyOrders.entrySet().stream()
				.limit(10)
				.map(entry -> new PriceLevelDto(
						entry.getKey(), calculateTotalQuantity(entry.getValue()), entry.getValue().size())
				).toList();
	}

	/**
	 * 총 주문 수량 계산
	 */
	private BigDecimal calculateTotalQuantity(ConcurrentSkipListSet<Order> orders) {
		return orders.stream()
				.map(Order::getRemainingQuantity)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * 종목별 요약 정보 조회
	 */
	public OrderSummaryResponse getSummary() {
		return new OrderSummaryResponse(
				companyCode,
				getOrderVolumeStats(sellOrders),
				getOrderVolumeStats(buyOrders)
		);
	}

	/**
	 * 주문 수량 통계 계산
	 */
	public Integer getOrderVolumeStats(final ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<Order>> orderMap) {
		return orderMap.values().stream()
				.mapToInt(ConcurrentSkipListSet::size)
				.sum();
	}
}
