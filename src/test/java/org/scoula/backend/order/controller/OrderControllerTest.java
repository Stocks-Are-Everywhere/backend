// package org.scoula.backend.order.controller;
//
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
// import java.math.BigDecimal;
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Queue;
// import java.util.TreeMap;
//
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.scoula.backend.global.jwt.JwtUtil;
// import org.scoula.backend.global.security.UserDetailsServiceImpl;
// import org.scoula.backend.member.exception.AccountNotFoundException;
// import org.scoula.backend.member.exception.MemberNotFoundException;
// import org.scoula.backend.mock.WithMockUserDetails;
// import org.scoula.backend.order.controller.request.OrderRequest;
// import org.scoula.backend.order.controller.response.OrderBookResponse;
// import org.scoula.backend.order.controller.response.OrderSnapshotResponse;
// import org.scoula.backend.order.controller.response.OrderSummaryResponse;
// import org.scoula.backend.order.controller.response.TradeHistoryResponse;
// import org.scoula.backend.order.domain.Order;
// import org.scoula.backend.order.domain.OrderStatus;
// import org.scoula.backend.order.domain.Type;
// import org.scoula.backend.order.dto.PriceLevelDto;
// import org.scoula.backend.order.service.OrderService;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.http.MediaType;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import org.springframework.test.web.servlet.MockMvc;
//
// import com.fasterxml.jackson.databind.ObjectMapper;
//
// @WebMvcTest(OrderController.class)
// class OrderControllerTest {
//
// 	@Autowired
// 	private MockMvc mockMvc;
//
// 	@MockitoBean
// 	private OrderService orderService;
//
// 	@Autowired
// 	private ObjectMapper objectMapper;
//
// 	@MockitoBean
// 	private JwtUtil jwtUtil;
//
// 	@MockitoBean
// 	private UserDetailsServiceImpl userDetailsServiceImpl;
//
// 	private final OrderRequest request = new OrderRequest(
// 			"005930",
// 			Type.BUY,
// 			new BigDecimal(100),
// 			new BigDecimal(100),
// 			OrderStatus.ACTIVE,
// 			new BigDecimal(1000),
// 			1L
// 	);
//
// 	@Test
// 	@DisplayName("TC20.3.1 주문 생성 테스트")
// 	@WithMockUserDetails
// 	void testReceived() throws Exception {
// 		OrderRequest request = OrderRequest.builder()
// 				.companyCode("AAPL")
// 				.type(Type.BUY)
// 				.totalQuantity(new BigDecimal("10"))
// 				.remainingQuantity(new BigDecimal("10"))
// 				.status(OrderStatus.ACTIVE)
// 				.price(new BigDecimal("150.00"))
// 				.accountId(1L)
// 				.build();
//
// 		mockMvc.perform(post("/api/order")
// 						.contentType(MediaType.APPLICATION_JSON)
// 						.content(objectMapper.writeValueAsString(request)))
// 				.andExpect(status().isOk());
// 		verify(orderService).placeOrder(any(OrderRequest.class), any(String.class));
// 	}
//
// 	@Test
// 	@DisplayName("TC20.3.2 종목별 주문장 스냅샷 조회 테스트")
// 	@WithMockUserDetails
// 	void testGetSnapshot() throws Exception {
// 		String companyCode = "AAPL";
// 		TreeMap<BigDecimal, Queue<Order>> sellOrders = new TreeMap<>();
// 		TreeMap<BigDecimal, Queue<Order>> buyOrders = new TreeMap<>();
// 		OrderSnapshotResponse response = new OrderSnapshotResponse(companyCode, sellOrders, buyOrders);
// 		when(orderService.getSnapshot(companyCode)).thenReturn(response);
//
// 		mockMvc.perform(get("/api/order/snapshot")
// 						.param("code", companyCode))
// 				.andExpect(status().isOk())
// 				.andExpect(content().json(objectMapper.writeValueAsString(response)));
// 	}
//
// 	@Test
// 	@DisplayName("TC20.3.3 종목별 호가창 조회 테스트")
// 	@WithMockUserDetails
// 	void testGetBook() throws Exception {
// 		String companyCode = "AAPL";
// 		List<PriceLevelDto> sellLevels = new ArrayList<>();
// 		List<PriceLevelDto> buyLevels = new ArrayList<>();
// 		OrderBookResponse response = OrderBookResponse.builder()
// 				.companyCode(companyCode)
// 				.sellLevels(sellLevels)
// 				.buyLevels(buyLevels)
// 				.build();
// 		when(orderService.getBook(companyCode)).thenReturn(response);
//
// 		mockMvc.perform(get("/api/order/book")
// 						.param("code", companyCode))
// 				.andExpect(status().isOk())
// 				.andExpect(content().json(objectMapper.writeValueAsString(response)));
// 	}
//
// 	@Test
// 	@DisplayName("TC20.3.4 종목별 주문 요약 조회 테스트")
// 	@WithMockUserDetails
// 	void testGetSummary() throws Exception {
// 		String companyCode = "AAPL";
// 		OrderSummaryResponse response = new OrderSummaryResponse(companyCode, 5, 5);
// 		when(orderService.getSummary(companyCode)).thenReturn(response);
//
// 		mockMvc.perform(get("/api/order/summary")
// 						.param("code", companyCode))
// 				.andExpect(status().isOk())
// 				.andExpect(content().json(objectMapper.writeValueAsString(response)));
// 	}
//
// 	@Test
// 	@DisplayName("TC20.3.5 채결된 주문 조회 테스트")
// 	@WithMockUserDetails
// 	void testGetTradeHistory() throws Exception {
// 		List<TradeHistoryResponse> response = new ArrayList<>();
// 		Long now = Instant.now().getEpochSecond();
// 		TradeHistoryResponse tradeHistory = TradeHistoryResponse.builder()
// 				.id(1L)
// 				.companyCode("AAPL")
// 				.sellOrderId(100L)
// 				.buyOrderId(101L)
// 				.quantity(new BigDecimal("10"))
// 				.price(new BigDecimal("150.00"))
// 				.tradeTime(now)
// 				.build();
// 		response.add(tradeHistory);
// 		when(orderService.getTradeHistory()).thenReturn(response);
//
// 		mockMvc.perform(get("/api/order/tradehistory"))
// 				.andExpect(status().isOk())
// 				.andExpect(content().json(objectMapper.writeValueAsString(response)));
// 	}
//
// 	@Test
// 	@DisplayName("사용자 정보가 존재하는 경우 주문 성공")
// 	@WithMockUserDetails
// 	void orderSuccess() throws Exception {
// 		// when & then
// 		mockMvc.perform(post("/api/order")
// 						.header("Authorization", "token")
// 						.contentType(MediaType.APPLICATION_JSON)
// 						.content(objectMapper.writeValueAsString(request)))
// 				.andExpect(status().isOk());
// 	}
//
// 	@Test
// 	@DisplayName("사용자 정보가 존재하지 않는 경우 주문 실패")
// 	void failedOrderWhenUserIsUnauthorized() throws Exception {
// 		mockMvc.perform(post("/api/order")
// 						.contentType(MediaType.APPLICATION_JSON)
// 						.content(objectMapper.writeValueAsString(request)))
// 				.andExpect(status().isForbidden());
// 	}
//
// 	@Test
// 	@DisplayName("사용자 정보가 존재하지 않는 경우 예외를 발생시키고 404 Not Found를 반환한다.")
// 	@WithMockUserDetails
// 	void orderFailedWhenMemberNotFoundException() throws Exception {
// 		// given
// 		doThrow(new MemberNotFoundException()).when(orderService).placeOrder(any(), any());
//
// 		// when & then
// 		mockMvc.perform(post("/api/order")
// 						.header("Authorization", "token")
// 						.contentType(MediaType.APPLICATION_JSON)
// 						.content(objectMapper.writeValueAsString(request)))
// 				.andExpect(status().isNotFound());
// 	}
//
// 	@Test
// 	@DisplayName("사용자의 계좌 정보를 조회할 수 없는 경우 예외를 발생시키고 404 Not Found를 반환한다.")
// 	@WithMockUserDetails
// 	void orderFailedWhenAccountNotFoundException() throws Exception {
// 		// given
// 		doThrow(new AccountNotFoundException()).when(orderService).placeOrder(any(), any());
//
// 		// when & then
// 		mockMvc.perform(post("/api/order")
// 						.header("Authorization", "token")
// 						.contentType(MediaType.APPLICATION_JSON)
// 						.content(objectMapper.writeValueAsString(request)))
// 				.andExpect(status().isNotFound());
// 	}
// }
