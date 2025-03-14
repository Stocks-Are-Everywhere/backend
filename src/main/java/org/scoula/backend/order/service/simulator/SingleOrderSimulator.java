// package org.scoula.backend.order.service.simulator;
//
// import java.math.BigDecimal;
// import java.util.Random;
// import java.util.concurrent.Executors;
// import java.util.concurrent.ScheduledExecutorService;
// import java.util.concurrent.TimeUnit;
//
// import org.scoula.backend.order.controller.request.OrderRequest;
// import org.scoula.backend.order.domain.OrderStatus;
// import org.scoula.backend.order.domain.Type;
// import org.scoula.backend.order.service.OrderService;
//
// import lombok.extern.slf4j.Slf4j;
//
// @Slf4j
// public class SingleOrderSimulator {
// 	private final OrderService orderService;
// 	private final Random random = new Random();
// 	private final ScheduledExecutorService executor;
//
// 	// 시뮬레이션 설정
// 	private static final String CODE = "005930"; // 삼성전자
// 	private static final BigDecimal BASE_PRICE = new BigDecimal("76000");
// 	private static final Integer PRICE_RANGE = 11400 / 100; // 기준가 +-11400원
// 	private static final Integer SECONDS_BETWEEN_ORDERS = 1;
// 	private static final Integer ORDER_INTERVAL = SECONDS_BETWEEN_ORDERS * 1000; // n초로 변환
//
// 	public SingleOrderSimulator(final OrderService orderService) {
// 		this.orderService = orderService;
// 		this.executor = Executors.newSingleThreadScheduledExecutor();
// 	}
//
// 	// 시뮬레이션 시작
// 	public void startSimulation() {
// 		executor.scheduleAtFixedRate(
// 				this::generateRandomOrder,
// 				0,
// 				ORDER_INTERVAL,
// 				TimeUnit.MILLISECONDS
// 		);
// 	}
//
// 	// 랜덤 주문 생성
// 	private void generateRandomOrder() {
// 		try {
// 			// 주문 생성 및 처리
// 			final OrderRequest request = createRandomOrderRequest();
// 			log.info(request.price().toPlainString());
// 			orderService.placeOrderAsync(request, "username");
//
// 		} catch (Exception e) {
// 			log.error("Error generating order", e);
// 		}
// 	}
//
// 	private OrderRequest createRandomOrderRequest() {
// 		final Type type = random.nextBoolean() ? Type.BUY : Type.SELL;
// 		final BigDecimal price = generateRandomPrice();
// 		final BigDecimal quantity = generateRandomQuantity();
// 		final OrderStatus orderStatus = random.nextBoolean() ? OrderStatus.ACTIVE : OrderStatus.MARKET;
// 		return OrderRequest.builder()
// 				.companyCode(CODE)
// 				.type(type)
// 				.totalQuantity(quantity)
// 				.remainingQuantity(quantity)
// 				.status(orderStatus)
// 				.price(price)
// 				.accountId(1L)
// 				.build();
// 	}
//
// 	// 50_000원 - 200_000원 기준 가격 견적
// 	private BigDecimal generateRandomPrice() {
// 		int priceOffset = (random.nextInt(PRICE_RANGE * 2) - PRICE_RANGE) * 100;
// 		return BASE_PRICE.add(BigDecimal.valueOf(priceOffset));
// 	}
//
// 	private BigDecimal generateRandomQuantity() {
// 		return BigDecimal.valueOf(random.nextInt(1000) + 100); // 100~11000주
// 	}
//
// 	// 시뮬레이션 중지
// 	public void stopSimulation() {
// 		executor.shutdown();
// 	}
//
// }
