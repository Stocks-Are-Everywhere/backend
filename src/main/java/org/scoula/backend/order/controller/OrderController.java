package org.scoula.backend.order.controller;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.scoula.backend.global.security.UserDetailsImpl;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
import org.scoula.backend.order.controller.response.OrderSummaryResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.service.OrderService;
import org.scoula.backend.order.service.exception.MatchingException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Slf4j
@RestController
@RequestMapping("/api/order")
@Tag(name = "주문 API", description = "주문 생성 및 채결 기록을 조회하는 컨트롤러 입니다.")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@Operation(summary = "주문 생성")
	@PostMapping
	public ResponseEntity<Void> received(
			@RequestBody final OrderRequest request,
			@AuthenticationPrincipal final UserDetailsImpl user
	) throws MatchingException {
		orderService.placeOrder(request, user.getUsername());
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "JSON 종목별 주문장 스냅샷 조회")
	@GetMapping("/snapshot")
	public ResponseEntity<OrderSnapshotResponse> getSnapshot(@RequestParam("code") final String companyCode) {
		return ResponseEntity.ok(orderService.getSnapshot(companyCode));
	}

	@Operation(summary = "JSON 종목별 호가창 조회")
	@GetMapping("/book")
	public ResponseEntity<OrderBookResponse> getBook(@RequestParam("code") final String companyCode) {
		return ResponseEntity.ok(orderService.getBook(companyCode));
	}

	@Operation(summary = "JSON 종목별 주문 요약 조회")
	@GetMapping("/summary")
	public ResponseEntity<OrderSummaryResponse> getSummary(@RequestParam("code") final String companyCode) {
		return ResponseEntity.ok(orderService.getSummary(companyCode));
	}

	@Operation(summary = "채결된 주문 조회")
	@GetMapping("/tradehistory")
	public ResponseEntity<List<TradeHistoryResponse>> getTradeHistory() {
		return ResponseEntity.ok(orderService.getTradeHistory());
	}

}
