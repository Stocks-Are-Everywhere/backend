package org.scoula.backend.order.service.batch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.service.OrderBookService;
import org.scoula.backend.order.service.OrderService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessorService {
	private final OrderService orderService;
	private final OrderTransactionService transactionService;
	private final OrderPersistentQueue orderQueue;

	@Qualifier("orderProcessingExecutor")
	private final Executor orderProcessingExecutor;

	@Qualifier("supportTasksExecutor")
	private final Executor supportTasksExecutor;

	@Qualifier("dbOperationsExecutor")
	private final Executor dbOperationsExecutor;

	/**
	 * 비동기 주문 처리
	 */
	public CompletableFuture<Void> orderAsync(final OrderRequest request, final String username) {
		// 검증 단계 - 직접 호출 (비동기 아님)
		try {
			transactionService.validateOrderWithTransaction(request);
		} catch (Exception e) {
			log.warn("주문 검증 실패: {}", e.getMessage());
			return CompletableFuture.failedFuture(e);
		}

		// 주문 생성부터 비동기로 처리
		return CompletableFuture.supplyAsync(() -> {
					try {
						return transactionService.createOrderWithTransaction(request, username);
					} catch (Exception e) {
						log.error("주문 생성 실패: {}", e.getMessage());
						throw new CompletionException(e);
					}
				}, dbOperationsExecutor)
				.thenAcceptAsync(order -> {
					try {
						// 큐 저장 및 후속 처리
						orderQueue.enqueue(order).join();

						// 별도 스레드에서 주문 처리
						CompletableFuture.runAsync(() -> {
							try {
								OrderBookService orderBook = orderService.getOrCreateOrderBook(order.getCompanyCode());
								transactionService.processOrderInNewTransaction(order, orderBook);
							} catch (Exception e) {
								log.error("주문 처리 실패: {}", e.getMessage());
							}

							// 웹소켓 메시지는 처리 성공 여부와 관계없이 전송
							try {
								OrderBookResponse response = orderService.getBook(order.getCompanyCode());
								orderService.sendOrderBookUpdate(order.getCompanyCode(), response);
							} catch (Exception e) {
								log.error("웹소켓 메시지 전송 실패: {}", e.getMessage());
							}
						}, supportTasksExecutor);
					} catch (Exception e) {
						log.error("주문 후처리 실패: {}", e.getMessage());
					}
				}, orderProcessingExecutor)
				.exceptionally(ex -> {
					log.error("주문 처리 중 오류: {}", ex.getMessage());
					return null;
				});
	}
}
