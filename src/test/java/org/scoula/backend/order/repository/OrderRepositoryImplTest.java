//package org.scoula.backend.order.repository;
//
//import static org.assertj.core.api.Assertions.*;
//
//import java.math.BigDecimal;
//import java.time.Instant;
//import java.time.LocalDateTime;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.MockitoAnnotations;
//import org.scoula.backend.member.domain.Account;
//import org.scoula.backend.member.domain.Company;
//import org.scoula.backend.member.domain.Member;
//import org.scoula.backend.member.domain.MemberRoleEnum;
//import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
//import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
//import org.scoula.backend.member.service.reposiotry.AccountRepository;
//import org.scoula.backend.member.service.reposiotry.MemberRepository;
//import org.scoula.backend.order.domain.Order;
//import org.scoula.backend.order.domain.OrderStatus;
//import org.scoula.backend.order.domain.Type;
//import org.scoula.backend.order.service.OrderService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.context.annotation.Import;
//
//@DataJpaTest
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@Import(OrderRepositoryImpl.class)
//class OrderRepositoryImplTest {
//
//	@Autowired
//	private OrderRepositoryImpl orderRepository;
//
//	@Autowired
//	private TestEntityManager entityManager;
//
//	private Order createSampleOrder(String companyCode, Type type, BigDecimal price, OrderStatus status) {
//		Long now = Instant.now().getEpochSecond();
//		return Order.builder()
//				.companyCode(companyCode)
//				.type(type)
//				.totalQuantity(new BigDecimal("10"))
//				.remainingQuantity(new BigDecimal("10"))
//				.status(status)
//				.price(price)
//				.timestamp(now)
//				.createdDateTime(LocalDateTime.now())
//				.updatedDateTime(LocalDateTime.now())
//				.build();
//	}
//
//	@BeforeEach
//	void setUp() {
//		entityManager.persist(createSampleOrder("005930", Type.BUY, new BigDecimal("50000"), OrderStatus.ACTIVE));
//		entityManager.persist(createSampleOrder("005930", Type.SELL, new BigDecimal("51000"), OrderStatus.ACTIVE));
//		entityManager.persist(createSampleOrder("035420", Type.BUY, new BigDecimal("200000"), OrderStatus.ACTIVE));
//	}
//
//	@Test
//	@DisplayName("TC3-1-1: 지정가 매수 주문 저장 테스트")
//	void TC3_1_1_지정가_매수_주문_저장() {
//		Order order = createSampleOrder("005930", Type.BUY, new BigDecimal("50000"), OrderStatus.ACTIVE);
//		Order savedOrder = orderRepository.save(order);
//
//		assertThat(savedOrder).isNotNull();
//		assertThat(savedOrder.getId()).isNotNull();
//		assertThat(savedOrder.getCompanyCode()).isEqualTo("005930");
//		assertThat(savedOrder.getType()).isEqualTo(Type.BUY);
//		assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.ACTIVE);
//	}
//
//	@Test
//	@DisplayName("TC3-1-3: 시장가 매수 주문 저장 테스트")
//	void TC3_1_3_시장가_매수_주문_저장() {
//		Order order = createSampleOrder("005930", Type.BUY, new BigDecimal("0"), OrderStatus.MARKET);
//		Order savedOrder = orderRepository.save(order);
//
//		assertThat(savedOrder).isNotNull();
//		assertThat(savedOrder.getId()).isNotNull();
//		assertThat(savedOrder.getCompanyCode()).isEqualTo("005930");
//		assertThat(savedOrder.getPrice()).isEqualTo("0");
//		assertThat(savedOrder.getType()).isEqualTo(Type.BUY);
//		assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.MARKET);
//	}
//
//	@Test
//	@DisplayName("TC3-2-1: 지정가 매도 주문 저장 테스트")
//	void TC3_2_1_지정가_매도_주문_저장() {
//		Order order = createSampleOrder("005930", Type.SELL, new BigDecimal("51000"), OrderStatus.ACTIVE);
//		Order savedOrder = orderRepository.save(order);
//
//		assertThat(savedOrder).isNotNull();
//		assertThat(savedOrder.getId()).isNotNull();
//		assertThat(savedOrder.getType()).isEqualTo(Type.SELL);
//	}
//
//	// @Test
//	// @DisplayName("TC3-3-1: 미체결 주문 취소 테스트")
//	// void TC3_3_1_미체결_주문_취소() {
//	// 	Order order = createSampleOrder("005930", Type.BUY, new BigDecimal("50000"), OrderStatus.ACTIVE);
//	// 	Order savedOrder = orderRepository.save(order);
//	//
//	// 	Order cancelledOrder = Order.builder()
//	// 		.id(savedOrder.getId())
//	// 		.companyCode(savedOrder.getCompanyCode())
//	// 		.type(savedOrder.getType())
//	// 		.totalQuantity(savedOrder.getTotalQuantity())
//	// 		.remainingQuantity(savedOrder.getRemainingQuantity())
//	// 		.status(OrderStatus.CANCEL)
//	// 		.price(savedOrder.getPrice())
//	// 		.timestamp(savedOrder.getTimestamp())
//	// 		.build();
//	//
//	// 	cancelledOrder = orderRepository.save(cancelledOrder);
//	//
//	// 	assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCEL);
//	// }
//
//	@Test
//	@DisplayName("TC20-1-2: 데이터 정합성 검증 테스트")
//	void TC20_1_2_데이터_정합성_검증() {
//		Order order = createSampleOrder("005930", Type.BUY, new BigDecimal("50000"), OrderStatus.ACTIVE);
//		Order savedOrder = orderRepository.save(order);
//
//		Order foundOrder = orderRepository.findById(savedOrder.getId()).orElse(null);
//		assertThat(foundOrder).isNotNull();
//		assertThat(foundOrder.getCompanyCode()).isEqualTo("005930");
//		assertThat(foundOrder.getType()).isEqualTo(Type.BUY);
//		assertThat(foundOrder.getPrice()).isEqualTo(new BigDecimal("50000"));
//		assertThat(foundOrder.getTotalQuantity()).isEqualTo(new BigDecimal("10"));
//		assertThat(foundOrder.getRemainingQuantity()).isEqualTo(new BigDecimal("10"));
//	}
//
//	/* @Test
//	void 주문_조회() {
//		List<Order> orders = orderRepository.findByCompanyCode("005930");
//
//		assertThat(orders).hasSize(2);
//		assertThat(orders).extracting(Order::getCompanyCode).containsOnly("005930");
//	} */
//
//	@Test
//	@DisplayName("주문 수량 업데이트 테스트")
//	void 주문_수량_업데이트() {
//		Order order = createSampleOrder("005930", Type.BUY, new BigDecimal("50000"), OrderStatus.ACTIVE);
//		Order savedOrder = orderRepository.save(order);
//
//		savedOrder.decreaseRemainingQuantity(new BigDecimal("5"));
//		Order updatedOrder = orderRepository.save(savedOrder);
//
//		assertThat(updatedOrder.getRemainingQuantity()).isEqualTo(new BigDecimal("5"));
//	}
//}
