package org.scoula.backend.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.global.jwt.JwtUtil;
import org.scoula.backend.global.security.UserDetailsServiceImpl;
import org.scoula.backend.member.exception.AccountNotFoundException;
import org.scoula.backend.member.exception.MemberNotFoundException;
import org.scoula.backend.mock.WithMockUserDetails;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.service.OrderService;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private final OrderRequest request = new OrderRequest(
            "005930",
            Type.BUY,
            new BigDecimal(100),
            new BigDecimal(100),
            OrderStatus.ACTIVE,
            new BigDecimal(1000),
            1L
    );

    @Test
    @DisplayName("사용자 정보가 존재하는 경우 주문 성공")
    @WithMockUserDetails
    void orderSuccess() throws Exception {
        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사용자 정보가 존재하지 않는 경우 주문 실패")
    void returnForbiddenWhenOrderWhenUserIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("사용자 정보가 존재하지 않는 경우 예외를 발생시키고 404 Not Found를 반환한다.")
    @WithMockUserDetails
    void returnNotFoundWhenMemberNotFound() throws Exception {
        // given
        doThrow(new MemberNotFoundException()).when(orderService).placeOrder(any(), any());

        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("사용자의 계좌 정보를 조회할 수 없는 경우 예외를 발생시키고 404 Not Found를 반환한다.")
    @WithMockUserDetails
    void returnNotFoundWhenAccountNotFound() throws Exception {
        // given
        doThrow(new AccountNotFoundException()).when(orderService).placeOrder(any(), any());

        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("전일 종가의 30% 미만 또는 초과하는 금액을 입력할 경우 404 BadRequest를 반환한다.")
    @WithMockUserDetails
    void returnBadRequestWhenPriceIsOutOfRange() throws Exception {
        // given
        doThrow(new PriceOutOfRangeException()).when(orderService).placeOrder(any(), any());
        OrderRequest outOfRangePriceRequest = new OrderRequest(
                "005930",
                Type.BUY,
                new BigDecimal(100),
                new BigDecimal(100),
                OrderStatus.ACTIVE,
                new BigDecimal(1001),
                1L
        );


        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outOfRangePriceRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("단위에 맞지 않는 금액을 입력할 경우 400 BadRequest를 반환한다.")
    @WithMockUserDetails
    void returnBadRequestWhenPriceUnitIsInvalid() throws Exception {
        // given
        doThrow(new PriceOutOfRangeException()).when(orderService).placeOrder(any(), any());
        OrderRequest invalidPriceUnitRequest = new OrderRequest(
                "005930",
                Type.BUY,
                new BigDecimal(100),
                new BigDecimal(100),
                OrderStatus.ACTIVE,
                new BigDecimal(1001),
                1L
        );

        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPriceUnitRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가격을 음수로 입력할 경우 400 BadRequest를 반환한다.")
    @WithMockUserDetails
    void returnBadRequestWhenPriceIsNegative() throws Exception {
        // given
        doThrow(new PriceOutOfRangeException()).when(orderService).placeOrder(any(), any());
        OrderRequest negativePrice = new OrderRequest(
                "005930",
                Type.BUY,
                new BigDecimal(100),
                new BigDecimal(100),
                OrderStatus.ACTIVE,
                new BigDecimal(1001),
                1L
        );

        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativePrice)))
                .andExpect(status().isBadRequest());
    }
}
