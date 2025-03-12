package org.scoula.backend.order.service.orderbook;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.PriceLevelDto;
import org.scoula.backend.order.service.TradeHistoryService;
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
	private final ConcurrentNavigableMap<Price, OrderStorage> sellOrders = new ConcurrentSkipListMap<>();
	// 매수 주문: 높은 가격 우선
	private final ConcurrentNavigableMap<Price, OrderStorage> buyOrders = new ConcurrentSkipListMap<>(Collections.reverseOrder());

	private final TradeHistoryService tradeHistoryService;

	/**
	 * 생성자
	 */
	public OrderBookService(final String companyCode, TradeHistoryService tradeHistoryService) {
		this.companyCode = companyCode;
		this.tradeHistoryService = tradeHistoryService;
	}

	/**
	 * 주문 접수 및 처리
	 */
	public void received(final Order order) {
		if (order.getStatus() == OrderStatus.MARKET) {
			processMarketOrder(order);
		} else {
			processLimitOrder(order);
		}
	}

	/**
	 * 시장가 주문 처리
	 */
	private void processMarketOrder(final Order order) {
		if (order.getType() == Type.BUY) {
			matchMarketBuyOrder(order);
		} else {
			matchMarketSellOrder(order);
		}
	}

	/**
	 * 시장가 매도 주문 처리 - TreeMap 읽기, 제거 발생
	 */
	private void matchMarketSellOrder(final Order sellOrder) {
		while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매수 주문 찾기
			Map.Entry<Price, OrderStorage> bestBuy = buyOrders.firstEntry();
			if (bestBuy == null) {
				throw new MatchingException("주문 체결 불가 : " + sellOrder.getRemainingQuantity());
			}

			// 주문 매칭 처리
			matchOrders(bestBuy.getValue(), sellOrder);

			// 매수 큐가 비었으면 제거
			if (bestBuy.getValue().isEmpty()) {
				synchronized (bestBuy.getKey()) {
					buyOrders.remove(bestBuy.getKey());
				}
			}
		}
	}

	/**
	 * 시장가 매수 주문 처리 - 읽기, 제거 발생
	 */
	private void matchMarketBuyOrder(final Order buyOrder) {
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매도 주문 찾기
			Map.Entry<Price, OrderStorage> bestSell = sellOrders.firstEntry();

			if (bestSell == null) {
				throw new MatchingException("주문 체결 불가 : " + buyOrder.getRemainingQuantity());
			}

			// 주문 매칭 처리
			matchOrders(bestSell.getValue(), buyOrder);

			// 매도 큐가 비었으면 제거
			if (bestSell.getValue().isEmpty()) {
				synchronized (bestSell.getKey()) {
					sellOrders.remove(bestSell.getKey());
				}
			}
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
	 * 지정가 매도 주문 처리 - TreeMap 읽기, 삭제 발생
	 */
	private void matchSellOrder(final Order sellOrder) {
		while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매도가보다 높거나 같은 매수 주문 찾기
			Map.Entry<Price, OrderStorage> bestBuy = buyOrders.firstEntry();

			if (bestBuy == null || bestBuy.getKey().isHigherThan(sellOrder.getPrice())) {
				// 매칭되는 매수 주문이 없으면 주문장에 추가
				if (sellOrder.getPrice().compareTo(BigDecimal.ZERO) != 0) {
					addToOrderBook(sellOrders, sellOrder);
				}
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
	 * 지정가 매수 주문 처리 -- TreeMap 읽기 발생, 제거 발생
	 */
	private void matchBuyOrder(final Order buyOrder) {
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매수가보다 낮거나 같은 매도 주문 찾기
			Map.Entry<Price, OrderStorage> bestSell = sellOrders.firstEntry();

			if (bestSell == null || bestSell.getKey().isLowerThan(buyOrder.getPrice())) {
				if (buyOrder.getPrice().compareTo(BigDecimal.ZERO) != 0) {
					addToOrderBook(buyOrders, buyOrder);
				}
				break;
			}

			// 주문 매칭 처리
			matchOrders(bestSell.getValue(), buyOrder);

			// 매수 큐가 비었으면 제거
			if (bestSell.getValue().isEmpty()) {
				buyOrders.remove(bestSell.getKey());
			}
		}
	}

	/**
	 * 주문 매칭 처리 - 변경 발생
	 */
	private synchronized void matchOrders(final OrderStorage existingOrders, final Order incomingOrder) {
		while (!existingOrders.isEmpty() && incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 1. 주문 매칭
			TradeHistoryResponse response = existingOrders.match(incomingOrder);

			// 2. 매수 / 매도 주문 체결 내역 저장
			tradeHistoryService.saveTradeHistory(response);
		}
	}

	/**
	 * 주문장에 주문 추가
	 */
	private synchronized void addToOrderBook(final NavigableMap<Price, OrderStorage> orderBook, final Order order) {
		orderBook.computeIfAbsent(
				new Price(order.getPrice()),
				k -> new OrderStorage()
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
	public synchronized OrderBookResponse getBook() {
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
						entry.getKey().getValue(), calculateTotalQuantity(entry.getValue()), entry.getValue().size())
				).toList();
	}

	/**
	 * 매수 호가창 정보 생성
	 */
	private List<PriceLevelDto> createBidLevels() {
		return this.buyOrders.entrySet().stream()
				.limit(10)
				.map(entry -> new PriceLevelDto(
						entry.getKey().getValue(), calculateTotalQuantity(entry.getValue()), entry.getValue().size())
				).toList();
	}

	/**
	 * 총 주문 수량 계산
	 */
	private BigDecimal calculateTotalQuantity(OrderStorage orders) {
		return orders.getElements().stream()
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
	public Integer getOrderVolumeStats(final SortedMap<Price, OrderStorage> orderMap) {
		return orderMap.values().stream()
				.mapToInt(OrderStorage::size)
				.sum();
	}
}
