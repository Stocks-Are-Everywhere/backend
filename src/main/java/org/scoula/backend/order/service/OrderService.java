package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.OrderDto;
import org.scoula.backend.order.repository.OrderRepositoryImpl;
import org.scoula.backend.order.service.exception.MatchingException;
import org.scoula.backend.order.service.exception.OrderRejectedException;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.scoula.backend.order.service.validator.OrderValidator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderService {

	// 종목 코드를 키로 하는 주문들
	private final ConcurrentHashMap<String, OrderBookService> orderBooks = new ConcurrentHashMap<>();

	private final SimpMessagingTemplate messagingTemplate;

	private final TradeHistoryService tradeHistoryService;

	private final CompanyRepositoryImpl companyRepository;

	private final AccountRepositoryImpl accountRepository;

	private final MemberRepositoryImpl memberRepository;

	private final OrderRepositoryImpl orderRepository;

	// 지정가 주문
	@Transactional
	public void placeOrder(final OrderRequest request, final String username) throws MatchingException {
		Account account = memberRepository.getByUsername(username).getAccount();
		// 매수 주문 시 주문 가능한 잔액이 있는지 검증
		validateBuyOrderBalance(account, request.price().multiply(request.totalQuantity()));

		// 매도 주문 시 주문 가능한 수량인지 검증
		validateSellOrderQuantity(account, request.totalQuantity(), request.companyCode());

		// 지정가 주문 가격 견적 유효성 검증
		final BigDecimal price = request.price();
		final OrderValidator validator = OrderValidator.getUnitByPrice(price);
		validator.isValidPrice(price);

		// 종가 기준 검증
		validateClosingPrice(price, request.companyCode());

		final Order order = createOrder(request, username);
		account.addPendingOrderBalance(order.getPrice().multiply(order.getTotalQuantity()));

		// 주문 처리
		processOrder(order);
	}

	// 매수 주문시 주문 가능한 잔액이 있는지 검증
	private void validateBuyOrderBalance(final Account account, final BigDecimal totalOrderPrice) {
		account.validateDepositBalance(totalOrderPrice);
	}

	// 매도 주문시 주문 가능한 수량인지 검증
	private void validateSellOrderQuantity(final Account account, final BigDecimal orderQuantity, final String companyCode) {
		// 1. 보유한 매수 주문 수량 확인
		final Optional<List<Order>> activeBuyOrders = orderRepository
			.findByTypeAndCompanyCodeAndAccountIdAndStatus(
				Type.BUY,
				companyCode,
				account.getId(),
				OrderStatus.COMPLETE // TODO: 지금 보유주식 아닐 가능성 높음. 수정안 논의 필요. 체결기록으로 사용도 동일함
			);

		if (activeBuyOrders.isEmpty()) {
			throw new OrderRejectedException("판매 가능한 보유 주식이 없습니다.");
		}

		// 2.보유 주문의 총 잔여 수량 계산
		// TODO: 지금 보유주식 아닐 가능성 높음. 수정안 논의 필요. 체결기록으로 사용도 동일함
		BigDecimal totalBuyQuantity = activeBuyOrders
			.map(orders -> orders.stream()
				.map(Order::getRemainingQuantity)
				.reduce(BigDecimal.ZERO, BigDecimal::add))
			.orElse(BigDecimal.ZERO);

		// 3. 기존 매도 예약 수량 계산
		BigDecimal reservedSellQuantity = orderRepository
			.findByTypeAndCompanyCodeAndAccountIdAndStatus(
				Type.SELL,
				companyCode,
				account.getId(),
				OrderStatus.ACTIVE
			)
			.map(orders -> orders.stream()
				.map(Order::getRemainingQuantity)
				.reduce(BigDecimal.ZERO, BigDecimal::add))
			.orElse(BigDecimal.ZERO);

		// 4. 주문 가능 수량 검증
		BigDecimal availableQuantity = totalBuyQuantity.subtract(reservedSellQuantity);
		if (availableQuantity.compareTo(orderQuantity) < 0) {
			throw new OrderRejectedException("주문 가능 수량을 초과했습니다.");
		}
	}

	// 종가 기준 가격 검증
	private void validateClosingPrice(final BigDecimal price, final String companyCode) {
		final Company company = companyRepository.findByIsuSrtCd(companyCode)
				.orElseThrow(PriceOutOfRangeException::new);

		if (!company.isWithinClosingPriceRange(price)) {
			throw new PriceOutOfRangeException();
		}
	}

	private Order createOrder(final OrderRequest request, final String username) {
		final Member member = memberRepository.getByUsername(username);
		final Account account = accountRepository.getByMemberId(member.getId());
		return new OrderDto(request).to(account);
	}

	// 주문 처리
	private void processOrder(final Order order) throws MatchingException {
		final OrderBookService orderBook = addOrderBook(order.getCompanyCode());
		orderBook.received(order);

		// 웹소켓 보내기
		final OrderBookResponse response = orderBook.getBook();
		broadcastOrderBookUpdate(response.companyCode(), response);
	}

	// 종목별 주문장 생성, 이미 존재할 경우 반환
	public OrderBookService addOrderBook(final String companyCode) {
		return orderBooks.computeIfAbsent(companyCode, k ->
				new OrderBookService(companyCode, tradeHistoryService));
	}

	// 주문 발생 시 호가창 업데이트 브로드캐스트
	private void broadcastOrderBookUpdate(final String code, final OrderBookResponse orderBook) {
		messagingTemplate.convertAndSend("/topic/orderbook/" + code, orderBook);
	}

	// JSON 종목별 주문장 스냅샷 생성
	public OrderSnapshotResponse getSnapshot(final String companyCode) {
		final OrderBookService orderBook = addOrderBook(companyCode);
		return orderBook.getSnapshot();
	}

	// JSON 종목별 호가창 생성
	public OrderBookResponse getBook(final String companyCode) {
		final OrderBookService orderBook = addOrderBook(companyCode);
		return orderBook.getBook();
	}

	// JSON 종목별 주문 요약 생성
	public OrderSummaryResponse getSummary(final String companyCode) {
		final OrderBookService orderBook = addOrderBook(companyCode);
		return orderBook.getSummary();
	}

	public List<TradeHistoryResponse> getTradeHistory() {
		return tradeHistoryService.getTradeHistory();
	}

}
