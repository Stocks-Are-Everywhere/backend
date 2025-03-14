// package org.scoula.backend.order.service.simulator;
//
// import org.scoula.backend.order.service.OrderService;
// import org.springframework.stereotype.Service;
//
// import lombok.extern.slf4j.Slf4j;
//
// @Slf4j
// @Service
// public class OrderSimulationService {
//
// 	private final SingleOrderSimulator singleOrderSimulator;
// 	private final MultiOrderSimulator multiOrderSimulator;
//
// 	public OrderSimulationService(final OrderService orderService) {
// 		this.singleOrderSimulator = new SingleOrderSimulator(orderService);
// 		this.multiOrderSimulator = new MultiOrderSimulator(orderService);
// 	}
//
// 	public void startSingleSimulation() {
// 		singleOrderSimulator.startSimulation();
// 	}
//
// 	public void stopSingleSimulation() {
// 		singleOrderSimulator.stopSimulation();
// 	}
//
// 	public void startMultiSimulation() {
// 		multiOrderSimulator.startSimulation();
// 	}
//
// 	// public void stopMultiSimulation() {
// 	// 	multiOrderSimulator.stopSimulation();
// 	// }
// }
