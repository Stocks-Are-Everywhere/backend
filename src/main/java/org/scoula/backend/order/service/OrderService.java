package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.exception.HoldingsNotFoundException;
import org.scoula.backend.member.repository.impls.HoldingsRepositoryImpl;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.OrderDto;
import org.scoula.backend.order.service.exception.CompanyNotFound;
import org.scoula.backend.order.service.exception.MatchingException;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.scoula.backend.order.service.validator.OrderValidator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderService {

	// 종목 코드를 키로 하는 주문들
	private final ConcurrentHashMap<String, OrderBookService> orderBooks = new ConcurrentHashMap<>();

	private final SimpMessagingTemplate messagingTemplate;

	private final TradeHistoryService tradeHistoryService;

	private final CompanyRepository companyRepository;

	private final AccountRepository accountRepository;

	private final MemberRepository memberRepository;

	private final OrderRepository orderRepository;

	private final HoldingsRepositoryImpl holdingsRepository;

	private final StockHoldingsService stockHoldingsService;

	private final AccountService accountService;

	public void placeOrder(final OrderRequest request, final String username) throws MatchingException {
		// 지정가 주문 가격 견적 유효성 검증
		final BigDecimal price = request.price();
		final OrderValidator validator = OrderValidator.getUnitByPrice(price);
		validator.isValidPrice(price);

		// 종가 기준 검증
		validateClosingPrice(price, request.companyCode());

		final Order order = createOrder(request, username);
		orderRepository.save(order);

		// 주문 처리
		processOrder(order);
	}

	// 종가 기준 가격 검증
	private void validateClosingPrice(final BigDecimal price, final String companyCode) {
		final Company company = companyRepository.findByIsuSrtCd(companyCode)
			.orElseThrow(CompanyNotFound::new);

		if (!company.isWithinClosingPriceRange(price)) {
			throw new PriceOutOfRangeException();
		}
	}

	private Order createOrder(final OrderRequest request, final String username) {
		final Account account = memberRepository.getByUsername(username).getAccount();

		// 매도 시 보유 주식 확인 및 보유 주식 수량 검증 후 예약 매도 수량 설정
		if (request.type() == Type.SELL) {
			final Holdings holdings = holdingsRepository.findByAccountIdAndCompanyCode(account.getId(),
					request.companyCode())
				.orElseThrow(() -> new HoldingsNotFoundException("보유 주식이 없습니다."));
			holdings.validateExistHoldings();
			holdings.validateEnoughHoldings(request.totalQuantity());
			holdings.processReservedOrder(request.totalQuantity());
		}
		// 매수 시 주문 가능 잔액 검증 후 예약 매수 금액 설정
		else {
			account.validateDepositBalance(request.price().multiply(request.totalQuantity()));
			account.processReservedOrder(request.price().multiply(request.totalQuantity()));
		}

		// 예약 주문 생성(매수/매도)
		return new OrderDto(request).to(account);
	}

	public void processOrder(final Order order) throws MatchingException {
		final OrderBookService orderBook = addOrderBook(order.getCompanyCode());
		orderBook.received(order);

		// 웹소켓 보내기
		final OrderBookResponse response = orderBook.getBook();
		broadcastOrderBookUpdate(response.companyCode(), response);
	}

	// 종목별 주문장 생성, 이미 존재할 경우 반환
	public OrderBookService addOrderBook(final String companyCode) {
		return orderBooks.computeIfAbsent(companyCode, k ->
			new OrderBookService(companyCode, tradeHistoryService, stockHoldingsService, accountService));
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

	public Map<String, OrderSummaryResponse> getAllOrderSummaries() {
		Map<String, OrderSummaryResponse> summaries = new HashMap<>();

		for (Map.Entry<String, OrderBookService> entry : orderBooks.entrySet()) {
			String companyCode = entry.getKey();
			OrderBookService orderBook = entry.getValue();
			OrderSummaryResponse summary = orderBook.getSummary();
			summaries.put(companyCode, summary);
		}

		return summaries;
	}

}
