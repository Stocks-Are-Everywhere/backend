package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.util.List;
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
import org.scoula.backend.order.dto.OrderDto;
import org.scoula.backend.order.service.exception.CompanyNotFound;
import org.scoula.backend.order.service.exception.MatchingException;
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

	// 지정가 주문
	@Transactional
	public void placeOrder(final OrderRequest request, final String username) throws MatchingException {
		// 지정가 주문 가격 견적 유효성 검증
		final BigDecimal price = request.price();
		final OrderValidator validator = OrderValidator.getUnitByPrice(price);
		validator.isValidPrice(price);

		// 종가 기준 검증
		validateClosingPrice(price, request.companyCode());

		final Order order = createOrder(request, username);

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
