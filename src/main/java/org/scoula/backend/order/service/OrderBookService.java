package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.TreeMap;

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

// 개별 종목의 주문장
@Slf4j
public class OrderBookService {
	// 종목 번호
	private final String companyCode;
	// 매도 주문: 낮은 가격 우선
	private final TreeMap<BigDecimal, Queue<Order>> sellOrders = new TreeMap<>();
	// 매수 주문: 높은 가격 우선
	private final TreeMap<BigDecimal, Queue<Order>> buyOrders = new TreeMap<>(Collections.reverseOrder());

	private final TradeHistoryService tradeHistoryService;

	public OrderBookService(final String companyCode,
			TradeHistoryService tradeHistoryService) {
		this.companyCode = companyCode;
		this.tradeHistoryService = tradeHistoryService;
	}

	// TODO : 채결 기준 (-15% - +15%) 적용 필요
	public void received(final Order order) throws MatchingException {
		// 가격이 0인 경우 시장가 주문으로 처리
		if (order.getStatus() == OrderStatus.MARKET) {
			if (order.getType() == Type.BUY) {
				matchMarketBuyOrder(order);
			} else {
				matchMarketSellOrder(order);
			}
		} else {
			// 기존 지정가 주문 처리
			if (order.getType() == Type.BUY) {
				matchBuyOrder(order);
			} else {
				matchSellOrder(order);
			}
		}
	}

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

	// TODO : 메서드에서 던지는 throws 제거 고민
	private void matchMarketSellOrder(final Order sellOrder) throws MatchingException {
		log.info("시장가 매도 메서드 진입");
		while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {

			// 매도가보다 높거나 같은 매수 주문 찾기
			Map.Entry<BigDecimal, Queue<Order>> bestBuy = buyOrders.firstEntry();
			if (bestBuy == null) {
				log.info("남은 시장가 매수 삭제");
				// 매칭되는 매수 주문이 없으면 남은 주문 그냥 삭제
				// TODO: 이대로 두어도 되나 ?
				throw new MatchingException("주문 체결 불가 : " + sellOrder.getRemainingQuantity());
			}
			// handleUnmatchedMarketOrder(buyOrder);

			// 주문 매칭 처리
			matchOrders(bestBuy.getValue(), sellOrder);

			// 매수 큐가 비었으면 제거
			if (bestBuy.getValue().isEmpty()) {
				buyOrders.remove(bestBuy.getKey());
			}
			log.info("시장가 매도 체결 완료");
		}
	}

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

			// 매수 큐가 비었으면 제거
			if (bestSell.getValue().isEmpty()) {
				sellOrders.remove(bestSell.getKey());
			}
		}
	}

	private void matchMarketBuyOrder(final Order buyOrder) throws MatchingException {
		log.info("시장가 매수 메서드 진입");
		while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			// 매수가보다 낮거나 같은 매도 주문 찾기
			Map.Entry<BigDecimal, Queue<Order>> bestSell = sellOrders.firstEntry();

			if (bestSell == null) {
				log.info("남은 시장가 매도 삭제");
				// 매칭되는 매도 주문이 없으면 남은 주문 그냥 삭제
				// TODO: 이대로 두어도 되나 ?
				throw new MatchingException("주문 체결 불가 : " + buyOrder.getRemainingQuantity());
			}
			// handleUnmatchedMarketOrder(buyOrder);

			// 주문 매칭 처리
			matchOrders(bestSell.getValue(), buyOrder);

			// 매수 큐가 비었으면 제거
			if (bestSell.getValue().isEmpty()) {
				sellOrders.remove(bestSell.getKey());
			}
		}
		log.info("시장가 매수 체결 완료");
	}

	private void matchOrders(final Queue<Order> existingOrders, final Order incomingOrder) {
		while (!existingOrders.isEmpty() &&
				incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
			final Order existingOrder = existingOrders.peek();

			final BigDecimal matchedQuantity = incomingOrder.getRemainingQuantity()
					.min(existingOrder.getRemainingQuantity());

			TradeHistoryResponse tradeHistory = TradeHistoryResponse.builder()
					// .sellOrderId(existingOrder.getType() == Type.SELL ?
					// 	existingOrder.getId() : incomingOrder.getId())
					// .buyOrderId(existingOrder.getType() == Type.BUY ?
					// 	existingOrder.getId() : incomingOrder.getId())
					.companyCode(existingOrder.getCompanyCode())
					.sellOrderId((long)123)
					.buyOrderId((long)456)
					.quantity(matchedQuantity)
					.price(existingOrder.getPrice())
					.tradeTime(LocalDateTime.now())
					.build();

			tradeHistoryService.saveTradeHistory(tradeHistory);
			log.info("db저장완료");
			//수량
			incomingOrder.updateQuantity(matchedQuantity);
			existingOrder.updateQuantity(matchedQuantity);

			// 완전 체결된 주문 제거
			if (existingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
				existingOrders.poll();
			}
		}
	}

	private void handleUnmatchedMarketOrder(final Order unmatchedOrder) {

	}

	private void addToOrderBook(final TreeMap<BigDecimal, Queue<Order>> orderBook, final Order order) {
		if (order.getPrice().compareTo(BigDecimal.ZERO) == 0) {
			log.warn("시장가 주문은 주문장에 추가할 수 없습니다: {}", order);
			return;
		}

		orderBook.computeIfAbsent(
				order.getPrice(),
				k -> new PriorityQueue<>(
						Comparator.comparing(Order::getTimestamp))
		).offer(order);
	}

	// 종목별 주문장 스냅샷 생성
	public OrderSnapshotResponse getSnapshot() {
		return new OrderSnapshotResponse(companyCode, sellOrders, buyOrders);
	}

	// 호가창 생성
	public OrderBookResponse getBook() {
		return OrderBookResponse.builder()
				.companyCode(companyCode)
				.sellLevels(createAskLevels())
				.buyLevels(createBidLevels())
				.build();
	}

	// 매도 호가창 정보
	private List<PriceLevelDto> createAskLevels() {
		return this.sellOrders.entrySet().stream()
				.limit(10)
				.sorted(Map.Entry.<BigDecimal, Queue<Order>>comparingByKey().reversed()) // 역순 정렬
				.map(entry -> new PriceLevelDto(
						entry.getKey(), calculateTotalQuantity(entry.getValue()), entry.getValue().size())
				).toList();
	}

	// 매수 호가창 정보
	private List<PriceLevelDto> createBidLevels() {
		return this.buyOrders.entrySet().stream()
				.limit(10)
				.map(entry -> new PriceLevelDto(
						entry.getKey(), calculateTotalQuantity(entry.getValue()), entry.getValue().size())
				).toList();
	}

	private BigDecimal calculateTotalQuantity(Queue<Order> orders) {
		return orders.stream()
				.map(Order::getRemainingQuantity) // remainingQuantity를 스트림으로 변환
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// 종목별 요약 정보 조회
	public OrderSummaryResponse getSummary() {
		return new OrderSummaryResponse(
				companyCode,
				getOrderVolumeStats(sellOrders),
				getOrderVolumeStats(buyOrders)
		);
	}

	// 주문 수량 분석
	public Integer getOrderVolumeStats(final TreeMap<BigDecimal, Queue<Order>> orderMap) {
		int count = 0;
		for (Queue<Order> orders : orderMap.values()) {
			for (Order order : orders) {
				count++;
			}
		}
		return count;
	}

}
