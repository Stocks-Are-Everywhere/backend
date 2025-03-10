package org.scoula.backend.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.service.exception.OrderPriceQuotationException;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
public class OrderLimitTest {

    @InjectMocks
    OrderService orderService;

    @Mock
    AccountRepositoryImpl accountRepository;

    @Mock
    MemberRepositoryImpl memberRepository;

    @Mock
    CompanyRepositoryImpl companyRepository;

    @Mock
    TradeHistoryService tradeHistoryService;

    @Mock
    SimpMessagingTemplate simpMessagingTemplate;

    Company company = Company.builder().isuCd("심상전자").isuNm("005930").closingPrice(new BigDecimal(1000)).build();
    Member member = Member.builder().id(1L).username("username").googleId("googleId").role(MemberRoleEnum.USER).build();

    @Test
    @DisplayName("입력된 가격이 종가 기준 상향 30% 이상일 경우 정상적으로 처리한다.")
    void placeOrderWhenPriceWithinUpperLimit() {
        // given
        BigDecimal price = new BigDecimal(1300);
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), price);
        when(companyRepository.findByIsuSrtCd(any())).thenReturn(Optional.of(company));
        when(memberRepository.getByUsername(any())).thenReturn(member);

        // when, then
        assertThatNoException().isThrownBy(() -> orderService.placeOrder(request, "username"));
    }

    @Test
    @DisplayName("입력된 가격이 종가 기준 상향 30%를 초과할 경우 예외가 발생한다.")
    void throwExceptionWhenPriceExceedsUpperLimit() {
        // given
        BigDecimal price = new BigDecimal(1301);
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), price);
        when(companyRepository.findByIsuSrtCd(any())).thenReturn(Optional.of(company));

        // when, then
        assertThatThrownBy(() -> orderService.placeOrder(request, "username")).isInstanceOf(PriceOutOfRangeException.class);
    }

    @Test
    @DisplayName("입력된 가격이 종가 기준 하향 30% 이하일 경우 정상적으로 처리한다.")
    void placeOrderWhenPriceWithinLowerLimit() {
        // given
        BigDecimal price = new BigDecimal(700);
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), price);
        when(companyRepository.findByIsuSrtCd(any())).thenReturn(Optional.of(company));
        when(memberRepository.getByUsername(any())).thenReturn(member);

        // when, then
        assertThatNoException().isThrownBy(() -> orderService.placeOrder(request, "username"));
    }

    @Test
    @DisplayName("입력된 가격이 종가 기준 하향 30% 미만일 경우 예외가 발생한다.")
    void throwExceptionWhenPriceIsBelowLowerLimit() {
        // given
        BigDecimal price = new BigDecimal(699);
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), price);
        when(companyRepository.findByIsuSrtCd(any())).thenReturn(Optional.of(company));

        // when, then
        assertThatThrownBy(() -> orderService.placeOrder(request, "username")).isInstanceOf(PriceOutOfRangeException.class);
    }

    @Test
    @DisplayName("입력 가격이 음수일 경우 예외가 발생한다.")
    void throwExceptionWhenInvalidPrice() {
        // given
        BigDecimal price = new BigDecimal(-1);
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), price);

        // when, then
        assertThatThrownBy(() -> orderService.placeOrder(request, "username")).isInstanceOf(OrderPriceQuotationException.class);
    }

    @Test
    @DisplayName("유효하지 않은 단위의 가격이 입력될 경우 예외가 발생한다.")
    void throwExceptionWhenInvalidUnitPrice() {
        // given
        BigDecimal price = new BigDecimal("0.5");
        OrderRequest request = createOrderRequest(Type.BUY, BigDecimal.valueOf(10), price);

        // when, then
        assertThatThrownBy(() -> orderService.placeOrder(request, "username")).isInstanceOf(OrderPriceQuotationException.class);
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
