package org.scoula.backend.order.service.batch;

import java.math.BigDecimal;

import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Holdings;
import org.scoula.backend.member.exception.HoldingsNotFoundException;
import org.scoula.backend.member.service.reposiotry.AccountRepository;
import org.scoula.backend.member.service.reposiotry.CompanyRepository;
import org.scoula.backend.member.service.reposiotry.HoldingsRepository;
import org.scoula.backend.member.service.reposiotry.MemberRepository;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.OrderDto;
import org.scoula.backend.order.service.OrderBookService;
import org.scoula.backend.order.service.exception.CompanyNotFound;
import org.scoula.backend.order.service.exception.MatchingException;
import org.scoula.backend.order.service.exception.PriceOutOfRangeException;
import org.scoula.backend.order.service.validator.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 트랜잭션 처리 전용 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {
	private final MemberRepository memberRepository;
	private final AccountRepository accountRepository;
	private final HoldingsRepository holdingsRepository;
	private final CompanyRepository companyRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
	public Order createOrderWithTransaction(final OrderRequest request, final String username) {
		try {
			final Account account = memberRepository.getByUsername(username).getAccount();

			// 매도 시 보유 주식 확인 및 보유 주식 수량 검증 후 예약 매도 수량 설정
			if (request.type() == Type.SELL) {
				final Holdings holdings = holdingsRepository.findByAccountIdAndCompanyCode(account.getId(),
								request.companyCode())
						.orElseThrow(() -> new HoldingsNotFoundException("보유 주식이 없습니다."));
				holdings.validateExistHoldings();
				holdings.validateEnoughHoldings(request.totalQuantity());
				holdings.processReservedOrder(request.totalQuantity());
				holdingsRepository.save(holdings);
			}
			// 매수 시 주문 가능 잔액 검증 후 예약 매수 금액 설정
			else {
				// 매수 가능 잔액 검증
				account.validateDepositBalance(request.price().multiply(request.totalQuantity()));
				account.processReservedOrder(request.price().multiply(request.totalQuantity()));
				accountRepository.save(account);
			}

			// 예약 주문 생성(매수/매도)
			final Order order = new OrderDto(request).to(account);

			// 디스크 큐를 사용할 때는 이 저장 과정을 생략하고 ID만 생성해야 함
			// 실제 데이터베이스 저장은 배치 프로세서에서 수행
			// return orderRepository.save(order);

			// ID만 할당하기 위해 JPA의 persist 메커니즘 사용
			// 실제 SQL은 트랜잭션 커밋 시까지 실행되지 않음
			entityManager.persist(order);
			// 즉시 flush하여 ID 할당 (실제 DB 저장은 최소화)
			entityManager.flush();

			return order;
		} catch (MatchingException e) {
			log.error("주문 생성 중 오류 발생: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * 주문 처리 - 새로운 트랙잭션
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
	public void processOrderInNewTransaction(final Order order, final OrderBookService orderBook) {
		orderBook.received(order);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 3)
	public void validateOrderWithTransaction(final OrderRequest request) {
		// CompanyRepository 조회 등 읽기 전용 작업
		final BigDecimal price = request.price();
		OrderValidator validator = OrderValidator.getUnitByPrice(price);
		validator.isValidPrice(price);

		final Company company = companyRepository.findByIsuSrtCd(request.companyCode())
				.orElseThrow(CompanyNotFound::new);

		if (!company.isWithinClosingPriceRange(price)) {
			throw new PriceOutOfRangeException();
		}
	}
}
