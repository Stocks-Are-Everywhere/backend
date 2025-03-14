package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.StockHoldingsService;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.service.batch.OrderPersistentQueue;
import org.scoula.backend.order.service.batch.OrderTransactionService;
import org.scoula.backend.order.service.exception.CompanyNotFound;
import org.scoula.backend.order.service.exception.MatchingException;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.scoula.backend.order.service.validator.OrderValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 처리 서비스
 * - 비동기 주문 처리
 * - 예외 처리
 * - 타임아웃 및 재시도 처리
 * - 트랜잭션 분리
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OrderService {

	// 종목 코드를 키로 하는 주문들
	private final ConcurrentHashMap<String, OrderBookService> orderBooks = new ConcurrentHashMap<>();

	// 외부 서비스 의존성
	private final SimpMessagingTemplate messagingTemplate;

	// 리포지토리 의존성
	private final CompanyRepository companyRepository;
	private final OrderRepository orderRepository;

	// 비즈니스 서비스 의존성
	private final StockHoldingsService stockHoldingsService;
	private final TradeHistoryService tradeHistoryService;
	private final AccountService accountService;

	// 스레드풀 의존성
	@Qualifier("orderProcessingExecutor")
	private final Executor orderProcessingExecutor;

	@Qualifier("supportTasksExecutor")
	private final Executor supportTasksExecutor;

	@Qualifier("dbOperationsExecutor")
	private final Executor dbOperationsExecutor;

	// 디스크 큐 의존성
	private final OrderPersistentQueue orderQueue;

	// 트랜잭션 서비스 의존성
	private final OrderTransactionService txService;

	// EntityManager 추가
	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * 비동기 주문 처리
	 */
	// public CompletableFuture<Void> placeOrderAsync(final OrderRequest request, final String username) {
	// 	// 1. 주문 유효성 검증
	// 	validateOrder(request);
	//
	// 	// 2. 주문 생성 (트랜잭션, 비동기 컨텍스트 외부 실행)
	// 	final Order order = txService.createOrderWithTransaction(request, username);
	//
	// 	return CompletableFuture.runAsync(() -> {
	// 		try {
	// 			log.info("비동기 주문 처리 시작: {}, 사용자: {}", request.companyCode(), username);
	//
	// 			// 3. 디스크 큐에 주문 저장 (orderRepository.save() 대신)
	// 			orderQueue.enqueue(order).join(); // 디스크 저장 완료 대기
	//
	// 			// 주문 처리 (매칭) - 트랜잭션 외부에서 실행
	// 			txService.processOrderInNewTransaction(order);
	//
	// 			log.info("비동기 주문 처리 완료: {}", order.getId());
	// 		} catch (MatchingException e) {
	// 			log.error("비동기 주문 처리 실패: {}", e.getMessage());
	// 			throw new MatchingException("비동기 주문 처리 실패");
	// 		}
	// 	}, orderProcessingExecutor).orTimeout(5, TimeUnit.SECONDS); // 타임아웃 추가
	// }

	/**
	 * 주문 유효성 검증
	 */
	public void validateOrder(final OrderRequest request) {
		// 주문 가격 검증
		final BigDecimal price = request.price();
		final OrderValidator validator = OrderValidator.getUnitByPrice(price);
		validator.isValidPrice(price);

		// 종가 기준 검증
		validateClosingPrice(price, request.companyCode());
	}

	/**
	 * 종가 기준 가격 검증
	 */
	private void validateClosingPrice(final BigDecimal price, final String companyCode) {
		final Company company = companyRepository.findByIsuSrtCd(companyCode)
				.orElseThrow(CompanyNotFound::new);

		if (!company.isWithinClosingPriceRange(price)) {
			throw new PriceOutOfRangeException();
		}
	}

	/**
	 * 트랜잭션 내에서 주문 생성
	 * - 메서드 분리
	 * - private 메서드는 프록시가 가로챌 수 없다.
	 */
	// @Transactional
	// private Order createOrderWithTransaction(final OrderRequest request, final String username) {
	// 	try {
	// 		final Account account = memberRepository.getByUsername(username).getAccount();
	//
	// 		// 매도 시 보유 주식 확인 및 보유 주식 수량 검증 후 예약 매도 수량 설정
	// 		if (request.type() == Type.SELL) {
	// 			final Holdings holdings = holdingsRepository.findByAccountIdAndCompanyCode(account.getId(),
	// 							request.companyCode())
	// 					.orElseThrow(() -> new HoldingsNotFoundException("보유 주식이 없습니다."));
	// 			holdings.validateExistHoldings();
	// 			holdings.validateEnoughHoldings(request.totalQuantity());
	// 			holdings.processReservedOrder(request.totalQuantity());
	// 			holdingsRepository.save(holdings);
	// 		}
	// 		// 매수 시 주문 가능 잔액 검증 후 예약 매수 금액 설정
	// 		else {
	// 			// 매수 가능 잔액 검증
	// 			account.validateDepositBalance(request.price().multiply(request.totalQuantity()));
	// 			account.processReservedOrder(request.price().multiply(request.totalQuantity()));
	// 			accountRepository.save(account);
	// 		}
	//
	// 		// 예약 주문 생성(매수/매도)
	// 		final Order order = new OrderDto(request).to(account);
	//
	// 		// 디스크 큐를 사용할 때는 이 저장 과정을 생략하고 ID만 생성해야 함
	// 		// 실제 데이터베이스 저장은 배치 프로세서에서 수행
	// 		// return orderRepository.save(order);
	//
	// 		// ID만 할당하기 위해 JPA의 persist 메커니즘 사용
	// 		// 실제 SQL은 트랜잭션 커밋 시까지 실행되지 않음
	// 		entityManager.persist(order);
	//
	// 		// 즉시 flush하여 ID 할당 (실제 DB 저장은 최소화)
	// 		entityManager.flush();
	//
	// 		return order;
	// 	} catch (MatchingException e) {
	// 		log.error("주문 생성 중 오류 발생: {}", e.getMessage(), e);
	// 		throw e;
	// 	}
	// }

	/**
	 * 주문 처리 - 트랜잭션 없이 실행될 내부 메서드
	 */
	// public void processOrder(final Order order) throws MatchingException {
	// 	// 1. 주문 매칭 처리
	// 	final OrderBookService orderBook = getOrCreateOrderBook(order.getCompanyCode());
	// 	orderBook.received(order);
	//
	// 	// 2. 호가창 업데이트 (비동기)
	// 	broadcastOrderBookUpdate(order.getCompanyCode());
	// }

	/**
	 * 종목별 주문장 생성 또는 조회
	 */
	public OrderBookService getOrCreateOrderBook(final String companyCode) {
		return orderBooks.computeIfAbsent(companyCode, k ->
				new OrderBookService(
						companyCode,
						tradeHistoryService,
						stockHoldingsService,
						accountService,
						orderRepository,
						dbOperationsExecutor,
						orderQueue)
		);
	}

	/**
	 * 웹소켓을 통한 호가창 업데이트 전송
	 */
	private void broadcastOrderBookUpdate(final String companyCode) {
		CompletableFuture.runAsync(() -> {
			try {
				final OrderBookResponse response = getBook(companyCode);
				messagingTemplate.convertAndSend("/topic/orderbook/" + companyCode, response);
			} catch (MatchingException e) {
				log.error("웹소켓 메시지 전송 중 오류 발생", e);
				// 웹소켓 전송 실패는 주문 처리 성공에 영향을 주지 않음
			}
		}, supportTasksExecutor);
	}

	/**
	 * 웹소켓 메시지 직접 전송 메서드
	 */
	public void sendOrderBookUpdate(String companyCode, OrderBookResponse response) {
		messagingTemplate.convertAndSend("/topic/orderbook/" + companyCode, response);
	}

	// JSON 종목별 주문장 스냅샷 생성
	public OrderSnapshotResponse getSnapshot(final String companyCode) {
		final OrderBookService orderBook = getOrCreateOrderBook(companyCode);
		return orderBook.getSnapshot();
	}

	// JSON 종목별 호가창 생성
	public OrderBookResponse getBook(final String companyCode) {
		final OrderBookService orderBook = getOrCreateOrderBook(companyCode);
		return orderBook.getBook();
	}

	// JSON 종목별 주문 요약 생성
	public OrderSummaryResponse getSummary(final String companyCode) {
		final OrderBookService orderBook = getOrCreateOrderBook(companyCode);
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
