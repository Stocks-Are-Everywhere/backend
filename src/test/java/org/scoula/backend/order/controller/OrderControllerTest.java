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
    void failedOrderWhenUserIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("사용자 정보가 존재하지 않는 경우 예외를 발생시키고 404 Not Found를 반환한다.")
    @WithMockUserDetails
    void orderFailedWhenMemberNotFoundException() throws Exception {
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
    void orderFailedWhenAccountNotFoundException() throws Exception {
        // given
        doThrow(new AccountNotFoundException()).when(orderService).placeOrder(any(), any());

        // when & then
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
