package org.scoula.backend.order.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
import org.scoula.backend.order.controller.response.OrderBookResponse;
import org.scoula.backend.order.domain.Order;
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
public class OrderBookIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(OrderBookIntegrationTest.class);

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

	private List<Member> buyers;
	private List<Member> sellers;
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

		// 테스트용 회원, 계정, 홀딩 생성
		buyers = new ArrayList<>();
		sellers = new ArrayList<>();

		for (int i = 0; i < 100; i++) {
			// 구매자 생성
			Member buyerMember = createMember("buyer" + i);
			buyers.add(buyerMember);

			// 판매자 생성
			Member sellerMember = createMember("seller" + i);
			sellers.add(sellerMember);

			// 판매자 홀딩 생성
			createHoldingsForSeller(sellerMember, companyCode);
		}
	}

	@Test
	public void testPartialOrderMatching() {
		// 테스트용 판매자와 구매자
		Member seller = sellers.get(0);
		Member buyer = buyers.get(0);

		// 판매자의 초기 Holdings 확인
		Holdings initialSellerHoldings = holdingsRepository.findByAccountIdAndCompanyCode(
				seller.getAccount().getId(), companyCode).orElseThrow();
		BigDecimal initialSellerQuantity = initialSellerHoldings.getQuantity();
		log.info("판매자 초기 보유량: {}", initialSellerQuantity);

		// 1. 매도 주문 생성 (큰 수량으로 생성하여 부분 체결 유도)
		BigDecimal sellPrice = new BigDecimal("90000");
		BigDecimal sellQuantity = new BigDecimal("5"); // 5주 판매

		OrderRequest sellRequest = OrderRequest.builder()
				.companyCode(companyCode)
				.type(Type.SELL)
				.price(sellPrice)
				.totalQuantity(sellQuantity)
				.remainingQuantity(sellQuantity)
				.status(OrderStatus.ACTIVE)
				.accountId(seller.getAccount().getId())
				.build();

		// 2. 매수 주문 생성 (소량으로 생성하여 부분 체결 유도)
		BigDecimal buyPrice = new BigDecimal("110000");
		BigDecimal buyQuantity = new BigDecimal("2"); // 2주만 구매

		OrderRequest buyRequest = OrderRequest.builder()
				.companyCode(companyCode)
				.type(Type.BUY)
				.price(buyPrice)
				.totalQuantity(buyQuantity)
				.remainingQuantity(buyQuantity)
				.status(OrderStatus.ACTIVE)
				.accountId(buyer.getAccount().getId())
				.build();

		// 3. 매도 주문 처리
		Order sellOrder = orderService.placeOrder(sellRequest, seller.getUsername());
		log.info("매도 주문 생성 - ID: {}, 상태: {}, 총 수량: {}",
				sellOrder.getId(), sellOrder.getStatus(), sellOrder.getTotalQuantity());

		// 4. 매수 주문 처리 (이 시점에서 부분 매칭이 발생해야 함)
		Order buyOrder = orderService.placeOrder(buyRequest, buyer.getUsername());
		log.info("매수 주문 생성 - ID: {}, 상태: {}, 총 수량: {}",
				buyOrder.getId(), buyOrder.getStatus(), buyOrder.getTotalQuantity());

		// 5. 처리 완료를 위한 짧은 대기
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// 6. 주문 상태 확인을 위해 DB에서 다시 조회
		Order updatedSellOrder = orderRepository.findById(sellOrder.getId()).orElseThrow();
		Order updatedBuyOrder = orderRepository.findById(buyOrder.getId()).orElseThrow();

		// 7. 상태 출력
		log.info("매칭 후 매도 주문 - ID: {}, 상태: {}, 잔여 수량: {}, 총 수량: {}",
				updatedSellOrder.getId(),
				updatedSellOrder.getStatus(),
				updatedSellOrder.getRemainingQuantity(),
				updatedSellOrder.getTotalQuantity());

		log.info("매칭 후 매수 주문 - ID: {}, 상태: {}, 잔여 수량: {}, 총 수량: {}",
				updatedBuyOrder.getId(),
				updatedBuyOrder.getStatus(),
				updatedBuyOrder.getRemainingQuantity(),
				updatedBuyOrder.getTotalQuantity());

		// 8. 주문장(OrderBook)에 매도 주문이 여전히 존재하는지 확인
		OrderBookResponse orderBook = orderService.getBook(companyCode);
		log.info("주문장 상태 - 매도 레벨: {}, 매수 레벨: {}",
				orderBook.sellLevels().size(), orderBook.buyLevels().size());

		// 매도 주문이 여전히 주문장에 존재하는지 확인 (부분 체결되었으므로)
		assertFalse(orderBook.sellLevels().isEmpty(), "부분 체결된 매도 주문이 주문장에 남아있어야 합니다.");

		// 가격 레벨 찾기
		boolean sellOrderFound = orderBook.sellLevels().stream()
				.anyMatch(level -> level.price().compareTo(sellPrice) == 0
						&& level.quantity().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(sellOrderFound, "매도 주문 가격 레벨이 주문장에 존재해야 합니다.");

		// 9. DB의 주문 상태 검증
		assertEquals(OrderStatus.ACTIVE, updatedSellOrder.getStatus(), "매도 주문은 부분 체결되어 여전히 활성 상태여야 합니다.");
		assertEquals(OrderStatus.COMPLETE, updatedBuyOrder.getStatus(), "매수 주문은 완전히 체결되어야 합니다.");

		// 10. 잔여 수량 검증
		assertEquals(0, sellQuantity.subtract(buyQuantity).compareTo(updatedSellOrder.getRemainingQuantity()),
				"매도 주문의 잔여 수량은 원래 수량에서 매수 수량을 뺀 값이어야 합니다.");
		assertEquals(0, BigDecimal.ZERO.compareTo(updatedBuyOrder.getRemainingQuantity()),
				"매수 주문의 잔여 수량은 0이어야 합니다.");

		// 11. Holdings 상태 검증
		Holdings sellerHoldings = holdingsRepository.findByAccountIdAndCompanyCode(
				seller.getAccount().getId(), companyCode).orElseThrow();
		Holdings buyerHoldings = holdingsRepository.findByAccountIdAndCompanyCode(
				buyer.getAccount().getId(), companyCode).orElse(null);

		// 매수자의 홀딩이 없으면 생성되었는지 확인
		assertNotNull(buyerHoldings, "매수자의 홀딩이 생성되어야 합니다.");

		log.info("매칭 후 판매자 홀딩 - 수량: {}, 예약 수량: {}",
				sellerHoldings.getQuantity(), sellerHoldings.getReservedQuantity());
		log.info("매칭 후 구매자 홀딩 - 수량: {}, 예약 수량: {}",
				buyerHoldings.getQuantity(), buyerHoldings.getReservedQuantity());

		// 매도자의 홀딩 수량 확인 (초기 수량 - 매도 수량)
		assertEquals(0, initialSellerQuantity.subtract(buyQuantity).compareTo(sellerHoldings.getQuantity()),
				"판매자의 홀딩 수량이 매도 수량만큼 감소해야 합니다.");

		// 매수자의 홀딩 수량 확인
		assertEquals(0, buyQuantity.compareTo(buyerHoldings.getQuantity()),
				"구매자의 홀딩 수량이 매수 수량만큼 증가해야 합니다.");

		// 예약 수량 확인 (남은 주문 수량만큼 예약되어 있어야 함)
		assertEquals(0, sellQuantity.subtract(buyQuantity).compareTo(sellerHoldings.getReservedQuantity()),
				"판매자의 예약 수량은 남은 주문 수량과 같아야 합니다.");
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

	private void createHoldingsForSeller(Member sellerMember, String companyCode) {
		// 판매자 홀딩 생성 로직 개선
		Holdings holdings = Holdings.builder()
				.account(sellerMember.getAccount())
				.companyCode(companyCode)
				.quantity(new BigDecimal("1000"))  // 판매 가능한 주식 수량
				.reservedQuantity(BigDecimal.ZERO)
				.averagePrice(new BigDecimal("100"))  // 평균 매입가
				.totalPurchasePrice(new BigDecimal("100000"))  // 총 매수 금액
				.build();

		holdingsRepository.save(holdings);
	}
}
