// package org.scoula.backend.order.controller;
//
// import org.scoula.backend.order.service.kiswebsocket.StockDataService;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;
//
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
// import lombok.RequiredArgsConstructor;
//
// @RestController
// @Tag(name = "한국투자 API", description = "한국투자로부터 실시간 채결가를 조회하는 컨트롤러 입니다.")
// @RequestMapping("/api/stock")
// @RequiredArgsConstructor
// public class StockDataController {
// 	private final StockDataService stockDataService;
//
// 	@Operation(summary = "한국투자 실시간 채결가 조회 start")
// 	@PostMapping("/subscribe/{code}")
// 	public ResponseEntity<String> subscribeStock(@PathVariable(name = "code") String code) {
// 		try {
// 			stockDataService.startStockDataStream(code);
// 			return ResponseEntity.ok("Connected to stock: " + code);
// 		} catch (Exception e) {
// 			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
// 					.body("Connection failed: " + e.getMessage());
// 		}
// 	}
//
// 	@Operation(summary = "한국투자 실시간 채결가 조회 stop")
// 	@PostMapping("/unsubscribe/{code}")
// 	public ResponseEntity<String> unsubscribeStock(@PathVariable(name = "code") String code) {
// 		try {
// 			stockDataService.stopStockDataStream(code);
// 			return ResponseEntity.ok("Disconnected from stock: " + code);
// 		} catch (Exception e) {
// 			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
// 					.body("Disconnection failed: " + e.getMessage());
// 		}
// 	}
//
// 	@Operation(summary = "한국투자 실시간 채결가 조회 start")
// 	@PostMapping("/subscribe/hoga/{code}")
// 	public ResponseEntity<String> subscribeHoga(@PathVariable(name = "code") String code) {
// 		try {
// 			stockDataService.startHogaDataStream(code);
// 			return ResponseEntity.ok("Connected to stock: " + code);
// 		} catch (Exception e) {
// 			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
// 					.body("Connection failed: " + e.getMessage());
// 		}
// 	}
//
// 	@Operation(summary = "한국투자 실시간 채결가 조회 stop")
// 	@PostMapping("/unsubscribe/hoga/{code}")
// 	public ResponseEntity<String> unsubscribeHoga(@PathVariable(name = "code") String code) {
// 		try {
// 			stockDataService.stopHogaDataStream(code);
// 			return ResponseEntity.ok("Disconnected from stock: " + code);
// 		} catch (Exception e) {
// 			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
// 					.body("Disconnection failed: " + e.getMessage());
// 		}
// 	}
// }
