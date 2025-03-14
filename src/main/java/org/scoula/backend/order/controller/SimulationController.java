// package org.scoula.backend.order.controller;
//
// import org.scoula.backend.order.service.simulator.OrderSimulationService;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;
//
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
// import lombok.RequiredArgsConstructor;
//
// // 테스트용 컨트롤러
// @RestController
// @RequestMapping("/api/simulation")
// @Tag(name = "주문 시뮬레이터 API", description = "실제 사용자들의 주문을 테스트하는 컨트롤러 입니다.")
// @RequiredArgsConstructor
// public class SimulationController {
//
// 	private final OrderSimulationService orderSimulationService;
//
// 	@Operation(summary = "싱글 스레드 시뮬레이터 start")
// 	@PostMapping("single/start")
// 	public ResponseEntity<String> startSingleSimulation() {
// 		orderSimulationService.startSingleSimulation();
// 		return ResponseEntity.ok("Single simulation started");
// 	}
//
// 	@Operation(summary = "싱글 스레드 시뮬레이터 stop")
// 	@PostMapping("single/stop")
// 	public ResponseEntity<String> stopSingleSimulation() {
// 		orderSimulationService.stopSingleSimulation();
// 		return ResponseEntity.ok("Single simulation stopped");
// 	}
//
// 	@Operation(summary = "멀티 스레드 시뮬레이터 start")
// 	@PostMapping("multi/start")
// 	public ResponseEntity<String> startMultiSimulation() {
// 		orderSimulationService.startMultiSimulation();
// 		return ResponseEntity.ok("Multi simulation started");
// 	}
//
// 	// @PostMapping("multi/stop")
// 	// public ResponseEntity<String> stopMultiSimulation() {
// 	// 	orderSimulationService.stopSingleSimulation();
// 	// 	return ResponseEntity.ok("Multi simulation stopped");
// 	// }
// }
