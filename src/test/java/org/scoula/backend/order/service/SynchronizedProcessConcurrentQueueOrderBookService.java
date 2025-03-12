package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.PriceLevelDto;
import org.scoula.backend.order.service.exception.MatchingException;

/**
 * 개별 종목의 주문장 - 시나리오 7: ConcurrentMap + Process Synchronized + ConcurrentLinkedQueue (AtomicReference Off)
 */
public class SynchronizedProcessConcurrentQueueOrderBookService {
	private final String companyCode;
	// 매도 주문: 낮은 가격 우선 (ConcurrentSkipListMap 사용)
	private final ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders = new ConcurrentSkipListMap<>();
	// 매수 주문: 높은 가격 우선 (ConcurrentSkipListMap 사용)
	private final ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders = new ConcurrentSkipListMap<>(
		Collections.reverseOrder());

	private final TradeHistoryService tradeHistoryService;
	private final StockHoldingsService stockHoldingsService;
	private final AccountService accountService;

	public SynchronizedProcessConcurrentQueueOrderBookService(final String companyCode,
		TradeHistoryService tradeHistoryService,
		StockHoldingsService stockHoldingsService, AccountService accountService) {
		this.companyCode = companyCode;
		this.tradeHistoryService = tradeHistoryService;
		this.stockHoldingsService = stockHoldingsService;
		this.accountService = accountService;
	}

	public void received(final Order order) throws MatchingException {
		System.out.println("주문 접수 - 종목: " + companyCode + ", 주문ID: " + order.getId() +
			", 타입: " + order.getType() + ", 가격: " + order.getPrice() +
			", 수량: " + order.getTotalQuantity());

		if (order.getStatus() == OrderStatus.MARKET) {
			processMarketOrder(order);
		} else {
			processLimitOrder(order);
		}

		System.out.println("주문 처리 완료 - 주문ID: " + order.getId() +
			", 잔여수량: " + order.getRemainingQuantity());
	}

	/**
	 * 시장가 주문 처리 (synchronized 적용)
	 */
	private synchronized void processMarketOrder(final Order order) throws MatchingException {
		if (order.getType() == Type.BUY) {
			matchMarketBuyOrder(order);
		} else {
			matchMarketSellOrder(order);
		}
	}

	/**
	 * 지정가 주문 처리 (synchronized 적용)
	 */
	private synchronized void processLimitOrder(final Order order) {
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
			// 매도가보다 높거나 같은 매수 주문 찾기
			Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> bestBuy = buyOrders.firstEntry();

			if (bestBuy == null || bestBuy.getKey().compareTo(sellOrder.getPrice()) < 0) {
				// 매칭되는 매수 주문이 없으면 주문장에 추가
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
		while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매수 주문 찾기
			Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> bestBuy = buyOrders.firstEntry();
			if (bestBuy == null) {
				throw new MatchingException("주문 체결 불가 : " + sellOrder.getRemainingQuantity());
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
	 * 지정가 매수 주문 처리
	 */
	private void matchBuyOrder(final Order buyOrder) {
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매수가보다 낮거나 같은 매도 주문 찾기
			Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> bestSell = sellOrders.firstEntry();

			if (bestSell == null || bestSell.getKey().compareTo(buyOrder.getPrice()) > 0) {
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
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매도 주문 찾기
			Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> bestSell = sellOrders.firstEntry();

			if (bestSell == null) {
				throw new MatchingException("주문 체결 불가 : " + buyOrder.getRemainingQuantity());
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
	 * 주문 매칭 처리
	 * ConcurrentLinkedQueue는 정렬 기능이 없으므로 시간 순서대로 정렬하는 로직 추가
	 */
	private void matchOrders(final ConcurrentLinkedQueue<Order> existingOrders, final Order incomingOrder) {
		// ConcurrentLinkedQueue에서 모든 주문을 가져와 시간 순서로 정렬
		List<Order> sortedOrders = new ArrayList<>(existingOrders);
		sortedOrders.sort(Comparator.comparing(Order::getTimestamp)
			.thenComparing(Order::getTotalQuantity, Comparator.reverseOrder()));

		// 정렬된 주문 목록을 기반으로 매칭 처리
		for (Order existingOrder : sortedOrders) {
			if (incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
				break; // 들어온 주문이 모두 체결되었으면 종료
			}

			// 이미 완전 체결된 주문은 건너뛰기
			if (existingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}

			// 동일 유저 주문 체결 방지
			if (incomingOrder.getAccount() != null && existingOrder.getAccount() != null &&
				incomingOrder.getAccount().getMember().equals(existingOrder.getAccount().getMember())) {
				continue; // 다음 주문으로 넘어감
			}

			final BigDecimal matchedQuantity = incomingOrder.getRemainingQuantity()
				.min(existingOrder.getRemainingQuantity());
			final BigDecimal matchPrice = existingOrder.getPrice(); // 체결 가격은 항상 기존 주문 가격

			// 1. 주문 수량 업데이트
			incomingOrder.decreaseRemainingQuantity(matchedQuantity);
			existingOrder.decreaseRemainingQuantity(matchedQuantity);

			// 2. 매수자/매도자 결정
			Order buyOrder, sellOrder;
			if (incomingOrder.isSellType()) {
				buyOrder = existingOrder;
				sellOrder = incomingOrder;
			} else {
				buyOrder = incomingOrder;
				sellOrder = existingOrder;
			}

			// 3. 거래 처리
			processTradeMatch(buyOrder, sellOrder, matchPrice, matchedQuantity);

			// 4. 완전 체결된 주문 제거
			if (existingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				existingOrders.remove(existingOrder);
			}
		}
	}

	// 매수/매도 주문 체결 처리
	private void processTradeMatch(Order buyOrder, Order sellOrder, BigDecimal price, BigDecimal quantity) {
		String companyCode = buyOrder.getCompanyCode();

		// 1. 거래 내역 저장
		TradeHistoryResponse tradeHistory = TradeHistoryResponse.builder()
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
	 * 주문장에 주문 추가 - ConcurrentLinkedQueue 사용
	 * ConcurrentLinkedQueue는 정렬 기능이 없으므로 삽입 시점에 시간 순서대로 들어가도록 함
	 */
	private void addToOrderBook(final ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> orderBook,
		final Order order) {
		if (order.getPrice().compareTo(BigDecimal.ZERO) == 0) {
			System.out.println("시장가 주문은 주문장에 추가할 수 없습니다: " + order);
			return;
		}

		// 해당 가격에 대한 큐가 없으면 새로 생성
		orderBook.computeIfAbsent(
			order.getPrice(),
			k -> new ConcurrentLinkedQueue<>()
		).add(order);
	}

	/**
	 * 호가창 생성
	 */
	public OrderBookResponse getBook() {
		final List<PriceLevelDto> sellLevels = createAskLevels();
		final List<PriceLevelDto> buyLevels = createBidLevels();
		return new OrderBookResponse(companyCode, sellLevels, buyLevels);
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
	private BigDecimal calculateTotalQuantity(ConcurrentLinkedQueue<Order> orders) {
		return orders.stream()
			.map(Order::getRemainingQuantity)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
