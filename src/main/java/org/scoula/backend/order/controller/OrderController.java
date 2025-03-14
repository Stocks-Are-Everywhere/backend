package org.scoula.backend.order.controller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import org.scoula.backend.member.exception.HoldingsNotFoundException;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.service.batch.OrderProcessorService;
import org.scoula.backend.order.service.exception.CompanyNotFound;
import org.scoula.backend.order.service.exception.MatchingException;
import org.scoula.backend.order.service.exception.OrderPriceQuotationException;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/order")
@Tag(name = "주문 API", description = "주문 생성 및 채결 기록을 조회하는 컨트롤러 입니다.")
@RequiredArgsConstructor
public class OrderController {

	private final OrderProcessorService orderProcessorService;

	@Operation(summary = "주문 생성")
	@PostMapping
	public CompletableFuture<ResponseEntity<Void>> received(
			@RequestBody final OrderRequest request
			// @AuthenticationPrincipal final UserDetailsImpl user
	) {
		return orderProcessorService.orderAsync(request, request.userName())
				.<ResponseEntity<Void>>thenApply(unused -> ResponseEntity.ok().build())
				.exceptionally(ex -> {
					Throwable cause = unwrapException(ex);

					if (cause instanceof HoldingsNotFoundException ||
							cause instanceof PriceOutOfRangeException ||
							cause instanceof CompanyNotFound ||
							cause instanceof MatchingException ||
							cause instanceof OrderPriceQuotationException) {
						return ResponseEntity.badRequest().build();
					} else if (cause instanceof TimeoutException) {
						return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
					} else {
						log.error("주문 처리 중 예외 발생", cause);
						return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
					}
				});
	}

	private Throwable unwrapException(Throwable ex) {
		return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
	}

	// @Operation(summary = "JSON 종목별 주문장 스냅샷 조회")
	// @GetMapping("/snapshot")
	// public ResponseEntity<OrderSnapshotResponse> getSnapshot(@RequestParam("code") final String companyCode) {
	// 	return ResponseEntity.ok(orderService.getSnapshot(companyCode));
	// }
	//
	// @Operation(summary = "JSON 종목별 호가창 조회")
	// @GetMapping("/book")
	// public ResponseEntity<OrderBookResponse> getBook(@RequestParam("code") final String companyCode) {
	// 	return ResponseEntity.ok(orderService.getBook(companyCode));
	// }
	//
	// @Operation(summary = "JSON 종목별 주문 요약 조회")
	// @GetMapping("/summary")
	// public ResponseEntity<OrderSummaryResponse> getSummary(@RequestParam("code") final String companyCode) {
	// 	return ResponseEntity.ok(orderService.getSummary(companyCode));
	// }
	//
	// @Operation(summary = "채결된 주문 조회")
	// @GetMapping("/tradehistory")
	// public ResponseEntity<List<TradeHistoryResponse>> getTradeHistory() {
	// 	return ResponseEntity.ok(orderService.getTradeHistory());
	// }

}
