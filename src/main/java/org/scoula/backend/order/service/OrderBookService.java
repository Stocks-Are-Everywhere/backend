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
import java.util.TreeMap;

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
import org.scoula.backend.order.service.exception.MatchingException;

import lombok.extern.slf4j.Slf4j;

/**
 * 개별 종목의 주문장
 */
@Slf4j
public class OrderBookService {
	// 종목 번호
	private final String companyCode;
	// 매도 주문: 낮은 가격 우선
	private final TreeMap<BigDecimal, Queue<Order>> sellOrders = new TreeMap<>();
	// 매수 주문: 높은 가격 우선
	private final TreeMap<BigDecimal, Queue<Order>> buyOrders = new TreeMap<>(
			Collections.reverseOrder());

	private final TradeHistoryService tradeHistoryService;

	private final StockHoldingsService stockHoldingsService;

	private final AccountService accountService;

	private final OrderRepository orderRepository;

	/**
	 * 생성자
	 */
	public OrderBookService(final String companyCode, final TradeHistoryService tradeHistoryService,
			final StockHoldingsService stockHoldingsService, final AccountService accountService,
			final OrderRepository orderRepository) {
		this.companyCode = companyCode;
		this.tradeHistoryService = tradeHistoryService;
		this.stockHoldingsService = stockHoldingsService;
		this.accountService = accountService;
		this.orderRepository = orderRepository;
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
	 * 지정가 매도 주문 처리
	 */
	private void matchSellOrder(final Order sellOrder) {
		while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			log.info("매도 메서드 진입");
			// 매도가보다 높거나 같은 매수 주문 찾기
			Map.Entry<BigDecimal, Queue<Order>> bestBuy = buyOrders.firstEntry();

			if (bestBuy == null || bestBuy.getKey().compareTo(sellOrder.getPrice()) < 0) {
				// 매칭되는 매수 주문이 없으면 주문장에 추가
				log.info("매도 초기값 할당 조건문 진입");
				addToOrderBook(sellOrders, sellOrder);
				break;
			}

			// 주문 매칭 처리
			matchOrders(bestBuy.getValue(), sellOrder);

			// 매수 큐가 비었으면 제거
			if (bestBuy.getValue().isEmpty()) {
				buyOrders.remove(bestBuy.getKey());
			}
		}
	}

	/**
	 * 시장가 매도 주문 처리
	 */
	private void matchMarketSellOrder(final Order sellOrder) throws MatchingException {
		log.info("시장가 매도 메서드 진입");
		while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매수 주문 찾기
			Map.Entry<BigDecimal, Queue<Order>> bestBuy = buyOrders.firstEntry();
			if (bestBuy == null) {
				log.info("남은 시장가 매수 삭제");
				throw new MatchingException("주문 체결 불가 : " + sellOrder.getRemainingQuantity());
			}

			// 주문 매칭 처리
			matchOrders(bestBuy.getValue(), sellOrder);

			// 매수 큐가 비었으면 제거
			if (bestBuy.getValue().isEmpty()) {
				buyOrders.remove(bestBuy.getKey());
			}
		}
		log.info("시장가 매도 체결 완료");
	}

	/**
	 * 지정가 매수 주문 처리
	 */
	private void matchBuyOrder(final Order buyOrder) {
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			log.info("매수 메서드 진입");
			// 매수가보다 낮거나 같은 매도 주문 찾기
			Map.Entry<BigDecimal, Queue<Order>> bestSell = sellOrders.firstEntry();

			if (bestSell == null || bestSell.getKey().compareTo(buyOrder.getPrice()) > 0) {
				log.info("매수 초기값 할당 조건문 진입");
				addToOrderBook(buyOrders, buyOrder);
				break;
			}

			// 주문 매칭 처리
			matchOrders(bestSell.getValue(), buyOrder);

			// 매도 큐가 비었으면 제거
			if (bestSell.getValue().isEmpty()) {
				sellOrders.remove(bestSell.getKey());
			}
		}
	}

	/**
	 * 시장가 매수 주문 처리
	 */
	private void matchMarketBuyOrder(final Order buyOrder) throws MatchingException {
		log.info("시장가 매수 메서드 진입");
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매도 주문 찾기
			Map.Entry<BigDecimal, Queue<Order>> bestSell = sellOrders.firstEntry();

			if (bestSell == null) {
				log.info("남은 시장가 매도 삭제");
				throw new MatchingException("주문 체결 불가 : " + buyOrder.getRemainingQuantity());
			}

			// 주문 매칭 처리
			matchOrders(bestSell.getValue(), buyOrder);

			// 매도 큐가 비었으면 제거
			if (bestSell.getValue().isEmpty()) {
				sellOrders.remove(bestSell.getKey());
			}
		}
		log.info("시장가 매수 체결 완료");
	}

	/**
	 * 주문 매칭 처리 - 상태 및 수량 변경 후 DB 업데이트 로직 추가
	 */
	private void matchOrders(final Queue<Order> existingOrders, final Order incomingOrder) {
		// 처리 중에 제외된 주문들을 임시 저장
		final Queue<Order> skippedOrders = new PriorityQueue<>(
				Comparator.comparing(Order::getTimestamp)
						.thenComparing(Order::getTotalQuantity, Comparator.reverseOrder())
		);

		// 변경된 주문을 추적하기 위한 Set
		final Set<Order> orderToUpdate = new HashSet<>();

		while (!existingOrders.isEmpty() && incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			final Order existingOrder = existingOrders.poll(); // 큐에서 제거

			// 동일 유저 주문인 경우
			if (incomingOrder.getAccount().getMember().equals(existingOrder.getAccount().getMember())) {
				// 임시 큐에 저장
				skippedOrders.offer(existingOrder);
				continue;
			}

			final BigDecimal matchedQuantity = incomingOrder.getRemainingQuantity()
					.min(existingOrder.getRemainingQuantity());
			final BigDecimal matchPrice = existingOrder.getPrice(); // 체결 가격은 항상 기존 주문 가격

			// 0. 매칭 전 값들 기록
			final BigDecimal originalIncomingRemaining = incomingOrder.getRemainingQuantity();
			final BigDecimal originalExistingRemaining = existingOrder.getRemainingQuantity();
			final OrderStatus originalIncomingStatus = incomingOrder.getStatus();
			final OrderStatus originalExistingStatus = existingOrder.getStatus();

			// 1. 주문 수량 업데이트
			incomingOrder.processMatch(matchedQuantity);
			existingOrder.processMatch(matchedQuantity);

			// 2. 상태나 남은 수량이 변경된 경우 업데이트할 주문 목록에 추가
			if (originalIncomingStatus != incomingOrder.getStatus()
					|| originalIncomingRemaining.compareTo(incomingOrder.getRemainingQuantity()) != 0) {
				orderToUpdate.add(incomingOrder);
			}
			if (originalExistingStatus != existingOrder.getStatus()
					|| originalExistingRemaining.compareTo(existingOrder.getRemainingQuantity()) != 0) {
				orderToUpdate.add(existingOrder);
			}

			// 3. 매수자/매도자 결정
			Order buyOrder, sellOrder;
			if (incomingOrder.isSellType()) {
				buyOrder = existingOrder;
				sellOrder = incomingOrder;
			} else {
				buyOrder = incomingOrder;
				sellOrder = existingOrder;
			}

			// 4. 거래 처리
			processTradeMatch(buyOrder, sellOrder, matchPrice, matchedQuantity);

			// 5. 완전 체결되지 않은 주문은 다시 큐에 추가
			if (!existingOrder.isCompletelyFilled()) {
				existingOrders.offer(existingOrder);
			}
		}

		// 6. 임시 큐에 저장했던 건너뛴 주문들을 다시 원래 큐에 추가
		while (!skippedOrders.isEmpty()) {
			existingOrders.offer(skippedOrders.poll());
		}

		// 7. 인커밍 주문의 남은 수량 처리
		if (!incomingOrder.isCompletelyFilled()) {
			// 주문 타입에 맞는 주문장에 남은 수량이 있는 주문 추가
			if (incomingOrder.getType() == Type.BUY) {
				addToOrderBook(buyOrders, incomingOrder);
			} else {
				addToOrderBook(sellOrders, incomingOrder);
			}
		}

		// 8. 변경된 주문들을 DB에 업데이트
		if (!orderToUpdate.isEmpty()) {
			log.info("변경된 주문 수: {}", orderToUpdate.size());

			orderToUpdate.forEach(order -> {
				try {
					final OrderStatus beforeStatus = order.getStatus();
					final BigDecimal beforeQuantity = order.getRemainingQuantity();

					final Order savedOrder = orderRepository.save(order);

					log.info("주문 DB 업데이트 성공 - 주문ID: {}, 상태: {} -> {}, 남은 수량: {} -> {}",
							savedOrder.getId(), beforeStatus, savedOrder.getStatus(),
							beforeQuantity, savedOrder.getRemainingQuantity());
				} catch (Exception e) {
					log.error("주문 DB 업데이트 실패 - 주문ID: {}, 오류: {}",
							order.getId(), e.getMessage(), e);
				}
			});
		} else {
			log.warn("변경된 주문이 없음 - 매칭은 발생했지만 상태 변경이 없음");
		}

		// 로깅 추가
		log.info("매칭 후 주문 상태 - 주문ID: {}, 남은 수량: {}, 상태: {}",
				incomingOrder.getId(), incomingOrder.getRemainingQuantity(), incomingOrder.getStatus());
	}

	// 매수/매도 주문 체결 처리
	private void processTradeMatch(
			final Order buyOrder, final Order sellOrder, final BigDecimal price, final BigDecimal quantity) {
		final String companyCode = buyOrder.getCompanyCode();
		log.info("매수/매도 주문 체결 처리 - 매수ID: {}, 매도ID: {}, 가격: {}, 수량: {}, 종목: {}",
				buyOrder.getId(), sellOrder.getId(), price, quantity, companyCode);

		// 1. 거래 내역 저장
		final TradeHistoryResponse tradeHistory = TradeHistoryResponse.builder()
				.companyCode(companyCode)
				.buyOrderId(buyOrder.getId())
				.sellOrderId(sellOrder.getId())
				.quantity(quantity)
				.price(price)
				.tradeTime(Instant.now().getEpochSecond())
				.build();
		tradeHistoryService.saveTradeHistory(tradeHistory);

		// 2. 계좌 잔액 처리
		accountService.updateAccountAfterTrade(Type.BUY, buyOrder.getAccount(), price, quantity);
		accountService.updateAccountAfterTrade(Type.SELL, sellOrder.getAccount(), price, quantity);

		// 3. 보유 주식 처리
		stockHoldingsService.updateHoldingsAfterTrade(Type.BUY, buyOrder.getAccount(), companyCode, price, quantity);
		stockHoldingsService.updateHoldingsAfterTrade(Type.SELL, sellOrder.getAccount(), companyCode, price, quantity);
	}

	/**
	 * 주문장에 주문 추가
	 */
	private void addToOrderBook(final TreeMap<BigDecimal, Queue<Order>> orderBook, final Order order) {
		if (order.getPrice().compareTo(BigDecimal.ZERO) == 0) {
			log.warn("시장가 주문은 주문장에 추가할 수 없습니다: {}", order);
			return;
		}

		orderBook.computeIfAbsent(
				order.getPrice(),
				k -> new PriorityQueue<>(
						Comparator.comparing(Order::getTimestamp)
								.thenComparing(Order::getTotalQuantity, Comparator.reverseOrder())
				)
		).offer(order);
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
		final List<PriceLevelDto> sellLevels = createAskLevels();
		final List<PriceLevelDto> buyLevels = createBidLevels();
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
	private BigDecimal calculateTotalQuantity(Queue<Order> orders) {
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
	public Integer getOrderVolumeStats(final TreeMap<BigDecimal, Queue<Order>> orderMap) {
		return orderMap.values().stream()
				.mapToInt(Queue::size)
				.sum();
	}
}
