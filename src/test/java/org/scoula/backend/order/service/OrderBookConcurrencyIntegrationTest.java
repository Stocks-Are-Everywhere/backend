package org.scoula.backend.order.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.scoula.backend.member.service.AccountService;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.HoldingsRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
// @Transactional
@ActiveProfiles("test")
public class OrderBookConcurrencyIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(OrderBookConcurrencyIntegrationTest.class);

	@Autowired
	private OrderService orderService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private HoldingsRepository holdingsRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private CompanyRepository companyRepository;

	private Member seller;
	private List<Member> buyers;
	private String companyCode = "TEST001";

	@BeforeEach
	public void setup() {
		// 테스트용 회사 생성
		Company testCompany = Company.builder()
				.isuCd(companyCode)
				.isuSrtCd(companyCode)
				.isuNm("테스트 회사")
				.isuAbbrv("테스트")
				.isuEngNm("Test Company")
				.listDd("20240101")
				.mktTpNm("KOSPI")
				.secugrpNm("주식")
				.sectTpNm("일반")
				.kindStkcertTpNm("보통주")
				.parval("5000")
				.listShrs("1000000")
				.closingPrice(new BigDecimal("100000")) // 100,000원으로 설정
				.build();

		companyRepository.save(testCompany);

		seller = createMember("seller");

		// 테스트용 회원, 계정, 홀딩 생성
		buyers = new ArrayList<>();

		// 구매자들 생성
		for (int i = 0; i < 10000; i++) {
			Member buyer = createMember("buyer" + i);
			buyers.add(buyer);
		}
	}

	@Test
	public void concurrentSellOrdersTest() throws Exception {
		int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
		ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

		// 1. 매도 주문 생성 (큰 수량으로 생성하여 부분 체결 유도)
		BigDecimal sellPrice = new BigDecimal("90000");
		// 총 보유량
		BigDecimal sellerTotalQuantity = new BigDecimal("10000");
		// 총 판매 주문량
		BigDecimal sellQuantity = new BigDecimal("10000"); // 100주 판매

		// 판매자 Holding 생성
		createHoldingsForSeller(seller, companyCode, sellerTotalQuantity);

		// 판매자의 초기 Holdings 확인
		Holdings initialSellerHoldings = holdingsRepository.findByAccountIdAndCompanyCode(
				seller.getAccount().getId(), companyCode).orElseThrow();
		BigDecimal initialSellerQuantity = initialSellerHoldings.getQuantity();
		log.info("판매자 초기 보유량: {}", initialSellerQuantity);

		OrderRequest sellRequest = OrderRequest.builder()
				.companyCode(companyCode)
				.type(Type.SELL)
				.price(sellPrice)
				.totalQuantity(sellQuantity)
				.remainingQuantity(sellQuantity)
				.status(OrderStatus.ACTIVE)
				.accountId(seller.getAccount().getId())
				.build();

		// 3. 매도 주문 처리
		orderService.placeOrder(sellRequest, seller.getUsername());

		BigDecimal unitQuantity = new BigDecimal("1");
		BigDecimal buyPrice = new BigDecimal("90000");

		// 동시 주문 실행
		List<CompletableFuture<Void>> futures = buyers.stream()
				.limit(10000)
				.map(buyer -> CompletableFuture.runAsync(() -> {
					try {
						OrderRequest buyRequest = OrderRequest.builder()
								.companyCode(companyCode)
								.type(Type.BUY)
								.price(buyPrice)
								.totalQuantity(unitQuantity)
								.remainingQuantity(unitQuantity)
								.status(OrderStatus.ACTIVE)
								.accountId(buyer.getAccount().getId())
								.build();

						orderService.placeOrder(buyRequest, buyer.getUsername());
					} catch (Exception e) {
						log.error("주문 처리 중 오류 발생", e);
						throw new RuntimeException(e);
					}
				}, executorService))
				.toList();

		// 모든 주문 완료 대기
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executorService.shutdown();
		assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));

		// 판매자 홀딩 상태 검증
		Holdings sellerHoldings = holdingsRepository.findByAccountIdAndCompanyCode(
				seller.getAccount().getId(), companyCode).orElseThrow();

		log.info("최종 판매자 홀딩 - 수량: {}, 예약 수량: {}",
				sellerHoldings.getQuantity(), sellerHoldings.getReservedQuantity());

		// 총 주문량 검증
		BigDecimal expectedQuantity = sellerTotalQuantity.subtract(
				BigDecimal.valueOf(buyers.size()).multiply(unitQuantity)
		);

		// 홀딩 수량과 예약 수량 검증
		assertEquals(0, expectedQuantity.compareTo(sellerHoldings.getQuantity()),
				"판매자의 최종 홀딩 수량이 일치해야 합니다.");

		// 예약 수량이 0인지 확인
		assertEquals(0, BigDecimal.ZERO.compareTo(sellerHoldings.getReservedQuantity()),
				"모든 주문이 처리된 후 예약 수량은 0이어야 합니다.");
	}

	private Member createMember(String username) {
		Member member = new Member(
				"google-" + username,
				username + "@example.com",
				MemberRoleEnum.USER
		);

		// 계정 생성 후 명시적으로 저장
		Account account = member.createAccount();
		memberRepository.save(member);
		accountRepository.save(account);

		return member;
	}

	private void createHoldingsForSeller(Member sellerMember, String companyCode, BigDecimal quantity) {
		Holdings holdings = Holdings.builder()
				.account(sellerMember.getAccount())
				.companyCode(companyCode)
				.quantity(quantity)  // 판매 가능한 주식 수량
				.reservedQuantity(BigDecimal.ZERO)
				.averagePrice(new BigDecimal("100"))  // 평균 매입가
				.totalPurchasePrice(quantity.multiply(new BigDecimal("100")))  // 총 매수 금액
				.build();

		holdingsRepository.save(holdings);
	}

}
