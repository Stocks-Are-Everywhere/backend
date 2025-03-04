package org.scoula.backend.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.member.exception.AccountNotFound;
import org.scoula.backend.member.exception.MemberNotFound;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    AccountRepositoryImpl accountRepository;

    @Mock
    MemberRepositoryImpl memberRepository;

    @Mock
    TradeHistoryService tradeHistoryService;

    @Test
    @DisplayName("입력받은 사용자에 대한 정보가 저장되어있지 않은 경우 예외를 반환한다.")
    void orderFailedWhenMemberNotFound() {
        // given
        when(memberRepository.getByUsername(any())).thenThrow(MemberNotFound.class);
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(1000));

        // when, then
        assertThatThrownBy(() -> orderService.placeOrder(request, "username"))
                .isInstanceOf(MemberNotFound.class);
    }

    private OrderRequest createOrderRequest(
            Type type,
            BigDecimal totalQuantity,
            BigDecimal price
    ) {
        return new OrderRequest(
                "005930",
                type,
                totalQuantity,
                totalQuantity,
                OrderStatus.ACTIVE,
                price,
                1L
        );
    }
}