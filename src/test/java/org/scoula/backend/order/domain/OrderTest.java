package org.scoula.backend.order.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

class OrderTest {

	private final Member member = Member.builder()
			.email("test@example.com")
			.username("testuser")
			.build();

	private Account account;

	@BeforeEach
	void setUp() {
		member.createAccount();
		account = member.getAccount();
	}

	@Test
	@DisplayName("주문 생성 및 조회 테스트")
	void testOrderCreationAndRetrieval() {
		Long now = Instant.now().getEpochSecond();
		Order order = Order.builder()
				.companyCode("005930")
				.type(Type.BUY)
				.totalQuantity(new BigDecimal("100"))
				.remainingQuantity(new BigDecimal("100"))
				.status(OrderStatus.ACTIVE)
				.price(new BigDecimal("50000"))
				.timestamp(now)
				.createdDateTime(LocalDateTime.now())
				.updatedDateTime(LocalDateTime.now())
				.account(account)
				.build();

		assertThat(order).isNotNull();
		assertThat(order.getCompanyCode()).isEqualTo("005930");
		assertThat(order.getType()).isEqualTo(Type.BUY);
		assertThat(order.getTotalQuantity()).isEqualByComparingTo(new BigDecimal("100"));
	}

	@Test
	@DisplayName("주문 수량 업데이트 테스트")
	void testDecreaseRemainingQuantity() {
		Long now = Instant.now().getEpochSecond();
		Order order = Order.builder()
				.companyCode("005930")
				.type(Type.BUY)
				.totalQuantity(new BigDecimal("100"))
				.remainingQuantity(new BigDecimal("100"))
				.status(OrderStatus.ACTIVE)
				.price(new BigDecimal("50000"))
				.timestamp(now)
				.createdDateTime(LocalDateTime.now())
				.updatedDateTime(LocalDateTime.now())
				.account(account)
				.build();

		order.decreaseRemainingQuantity(new BigDecimal("20"));

		assertThat(order.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("80"));
	}

	@Test
	@DisplayName("모든 getter 메서드 테스트")
	void testAllGetters() {
		Long now = Instant.now().getEpochSecond();
		Order order = Order.builder()
				.companyCode("005930")
				.type(Type.BUY)
				.totalQuantity(new BigDecimal("100"))
				.remainingQuantity(new BigDecimal("100"))
				.status(OrderStatus.ACTIVE)
				.price(new BigDecimal("50000"))
				.timestamp(now)
				.account(account)
				.build();

		assertThat(order.getCompanyCode()).isEqualTo("005930");
		assertThat(order.getType()).isEqualTo(Type.BUY);
		assertThat(order.getTotalQuantity()).isEqualByComparingTo(new BigDecimal("100"));
		assertThat(order.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("100"));
		assertThat(order.getStatus()).isEqualTo(OrderStatus.ACTIVE);
		assertThat(order.getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
		assertThat(order.getTimestamp()).isEqualTo(now);
	}

	@Test
	@DisplayName("주문수량 0 테스트")
	void testEdgeCases() {
		Long now = Instant.now().getEpochSecond();
		Order order = Order.builder()
				.companyCode("005930")
				.type(Type.BUY)
				.totalQuantity(BigDecimal.ZERO)
				.remainingQuantity(BigDecimal.ZERO)
				.status(OrderStatus.ACTIVE)
				.price(BigDecimal.ZERO)
				.timestamp(now)
				.createdDateTime(LocalDateTime.now())
				.updatedDateTime(LocalDateTime.now())
				.account(account)
				.build();

		assertThat(order.getTotalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(order.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("빌더 toString 메서드 테스트")
	void testBuilderToString() {
		String builderString = Order.builder()
				.companyCode("005930")
				.type(Type.BUY)
				.totalQuantity(new BigDecimal("100"))
				.toString();
		assertThat(builderString).contains("companyCode", "type", "totalQuantity");
	}
}

