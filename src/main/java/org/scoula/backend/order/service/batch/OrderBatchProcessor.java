package org.scoula.backend.order.service.batch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.service.OrderRepository;
import org.scoula.backend.order.service.exception.MatchingException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 배치 처리 서비스
 * - 주문 생성 및 업데이트를 배치로 처리
 * - 디스크 큐에서 주문 데이터를 읽고 DB에 저장
 * - 주기적인 스케줄링으로 일괄 처리
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderBatchProcessor {

	private final OrderPersistentQueue orderQueue;
	private final OrderRepository orderRepository;

	// 배치 처리 설정
	private static final int BATCH_SIZE = 20;
	private static final int MAX_RETRY_COUNT = 3;

	// 회복 중 플래그
	private final AtomicBoolean recovering = new AtomicBoolean(false);

	// 서버 시작 시 초기화 및 회복 작업
	@PostConstruct
	public void initialize() {
		recovering.set(true);
		try {
			log.info("서버 시작 - 미처리 주문 데이터 복구 시작");
			recoverUnprocessedOrders();
			log.info("미처리 주문 데이터 복구 완료");
		} finally {
			recovering.set(false);
		}
	}

	/**
	 * 주기적인 배치 처리 실행
	 * - 1초마다 실행
	 * - 새 주문과 업데이트를 순차적으로 처리
	 */
	@Scheduled(fixedDelay = 1000)
	public void processBatch() {
		// 복구 작업 중에는 스케줄링된 배치 처리 건너뛰기
		if (recovering.get()) {
			return;
		}

		// 1. 새 주문 처리
		processNewOrders();

		// 2. 업데이트 처리
		processOrderUpdates();
	}

	/**
	 * 새 주문 배치 처리
	 */
	public void processNewOrders() {
		List<Order> orders = orderQueue.dequeueNewOrdersForProcessing(BATCH_SIZE);

		if (orders.isEmpty()) {
			return;
		}

		log.info("주문 생성 배치 처리 시작: {} 건", orders.size());

		saveOrdersInTransaction(orders);
	}

	@Transactional
	private void saveOrdersInTransaction(List<Order> orders) {
		try {
			List<Order> savedOrders = orderRepository.saveAll(orders);
			// 트랜잭션 내에서 큐 작업까지 완료
			orderQueue.removeProcessedNewOrders(savedOrders);
		} catch (Exception e) {
			log.error("주문 생성 배치 처리 실패: {}", e.getMessage(), e);
			throw e; // 예외를 다시 던져 트랜잭션이 롤백되도록 함
		}
	}

	/**
	 * 주문 업데이트 배치 처리
	 */
	public void processOrderUpdates() {
		List<Order> orders = orderQueue.dequeueUpdatesForProcessing(BATCH_SIZE);

		if (orders.isEmpty()) {
			return;
		}

		log.info("주문 업데이트 배치 처리 시작: {} 건", orders.size());

		try {
			// ID 기준으로 그룹화하여 최신 상태만 반영
			Map<Long, Order> latestOrderByIdMap = orders.stream()
					.collect(Collectors.toMap(
							Order::getId,
							order -> order,
							(existing, replacement) -> {
								if (replacement.getTimestamp().compareTo(existing.getTimestamp()) > 0) {
									return replacement;
								}
								return existing;
							}
					));

			List<Order> latestOrders = new ArrayList<>(latestOrderByIdMap.values());

			// 존재하는 주문과 존재하지 않는 주문 ID 처리를 별도 트랜잭션으로 분리
			ProcessResult result = processOrdersInTransaction(latestOrders);

			// DB에 없는 주문 ID 처리 (트랜잭션 외부)
			if (!result.notFoundIds.isEmpty()) {
				log.warn("업데이트할 주문 중 DB에 없는 항목: {} 건", result.notFoundIds.size());
				orderQueue.removeNonExistentUpdates(new ArrayList<>(result.notFoundIds.keySet()));
			}

			log.info("주문 업데이트 배치 처리 완료: {} 건 (처리됨), {} 건 (건너뜀)",
					result.savedOrders.size(), result.notFoundIds.size());

			// 처리 완료된 주문만 큐에서 제거 (트랜잭션 외부)
			orderQueue.removeProcessedUpdates(result.savedOrders);

		} catch (Exception e) {
			log.error("주문 업데이트 배치 처리 실패: {}", e.getMessage(), e);
			retryFailedOrdersIndividually(orders, true);
		}
	}

	// 트랜잭션 범위를 명확히 분리한 메서드
	@Transactional
	private ProcessResult processOrdersInTransaction(List<Order> orders) {
		List<Order> savedOrders = new ArrayList<>();
		Map<Long, String> notFoundIds = new HashMap<>();

		for (Order order : orders) {
			try {
				if (orderRepository.existsById(order.getId())) {
					savedOrders.add(orderRepository.save(order));
				} else {
					notFoundIds.put(order.getId(), order.getCompanyCode());
				}
			} catch (Exception e) {
				log.error("주문 개별 업데이트 실패 - ID: {}, 오류: {}", order.getId(), e.getMessage());
			}
		}

		return new ProcessResult(savedOrders, notFoundIds);
	}

	// 처리 결과를 담는 내부 클래스
	private static class ProcessResult {
		final List<Order> savedOrders;
		final Map<Long, String> notFoundIds;

		ProcessResult(List<Order> savedOrders, Map<Long, String> notFoundIds) {
			this.savedOrders = savedOrders;
			this.notFoundIds = notFoundIds;
		}
	}

	/**
	 * 배치 실패 시 개별 주문별 재시도
	 */
	private void retryFailedOrdersIndividually(List<Order> orders, boolean isUpdate) {
		log.info("배치 처리 실패 - 개별 처리 시도: {} 건", orders.size());

		List<Order> successOrders = new ArrayList<>();
		Map<Long, Integer> retryCountMap = new HashMap<>();  // 주문별 재시도 횟수 추적

		// 모든 주문이 처리되거나 최대 재시도 횟수에 도달할 때까지 반복
		List<Order> remainingOrders = new ArrayList<>(orders);

		while (!remainingOrders.isEmpty()) {
			List<Order> stillFailedOrders = new ArrayList<>();

			for (Order order : remainingOrders) {
				// 재시도 횟수 증가
				int retryCount = retryCountMap.getOrDefault(order.getId(), 0) + 1;
				retryCountMap.put(order.getId(), retryCount);

				// 최대 재시도 횟수 초과 시 로그만 남기고 다음 주문으로
				if (retryCount > MAX_RETRY_COUNT) {
					log.error("주문 처리 최대 재시도 횟수 초과 - ID: {}, 시도 횟수: {}",
							order.getId(), retryCount - 1);
					continue;
				}

				try {
					if (isUpdate) {
						// 업데이트인 경우 존재 여부 확인
						if (orderRepository.existsById(order.getId())) {
							Order savedOrder = orderRepository.save(order);
							successOrders.add(savedOrder);
							log.debug("주문 개별 업데이트 성공: {}, 시도 횟수: {}",
									savedOrder.getId(), retryCount);
						} else {
							log.warn("업데이트할 주문이 DB에 없음 (개별 처리): {}", order.getId());
							// DB에 없는 주문도 처리 완료로 표시 (무한 재시도 방지)
							successOrders.add(order);
						}
					} else {
						// 새 주문인 경우
						Order savedOrder = orderRepository.save(order);
						successOrders.add(savedOrder);
						log.debug("주문 개별 저장 성공: {}, 시도 횟수: {}",
								savedOrder.getId(), retryCount);
					}
				} catch (Exception e) {
					log.error("주문 개별 처리 실패 - ID: {}, 오류: {}, 시도 횟수: {}",
							order.getId(), e.getMessage(), retryCount);

					// 아직 최대 재시도 횟수에 도달하지 않았다면 다음 반복에서 재시도
					if (retryCount < MAX_RETRY_COUNT) {
						stillFailedOrders.add(order);
					}
				}
			}

			// 재시도할 주문이 없으면 반복 종료
			if (stillFailedOrders.isEmpty()) {
				break;
			}

			// 일정 시간 대기 후 재시도 (지수 백오프 적용 가능)
			try {
				Thread.sleep(100 * Math.min(10, retryCountMap.size()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}

			remainingOrders = stillFailedOrders;
		}

		// 성공한 주문은 큐에서 제거
		if (!successOrders.isEmpty()) {
			try {
				if (isUpdate) {
					orderQueue.removeProcessedUpdates(successOrders);
				} else {
					orderQueue.removeProcessedNewOrders(successOrders);
				}
				log.info("개별 처리 완료 주문 큐에서 제거: {} 건", successOrders.size());
			} catch (Exception e) {
				log.error("개별 처리된 주문 큐 제거 실패: {}", e.getMessage(), e);
			}
		}
	}

	/**
	 * 서버 다시 시작 시 미처리 주문 데이터 복구
	 */
	private void recoverUnprocessedOrders() {
		try {
			// 큐에서 미처리 주문 모두 가져와서 처리
			int processedNewOrders = 0;
			int processedUpdates = 0;

			// 새 주문 복구 (최대 10번 반복)
			for (int i = 0; i < 10; i++) {
				List<Order> newOrders = orderQueue.dequeueNewOrdersForProcessing(BATCH_SIZE);
				if (newOrders.isEmpty()) {
					break;
				}

				try {
					List<Order> savedOrders = orderRepository.saveAll(newOrders);
					orderQueue.removeProcessedNewOrders(savedOrders);
					processedNewOrders += savedOrders.size();
				} catch (Exception e) {
					log.error("복구 중 새 주문 배치 처리 실패: {}", e.getMessage(), e);
					retryFailedOrdersIndividually(newOrders, false);
				}
			}

			// 업데이트 복구 (최대 10번 반복)
			for (int i = 0; i < 10; i++) {
				List<Order> updates = orderQueue.dequeueUpdatesForProcessing(BATCH_SIZE);
				if (updates.isEmpty()) {
					break;
				}

				try {
					List<Order> savedOrders = new ArrayList<>();
					for (Order order : updates) {
						if (orderRepository.existsById(order.getId())) {
							savedOrders.add(orderRepository.save(order));
						}
					}
					orderQueue.removeProcessedUpdates(savedOrders);
					processedUpdates += savedOrders.size();
				} catch (Exception e) {
					log.error("복구 중 주문 업데이트 배치 처리 실패: {}", e.getMessage(), e);
					retryFailedOrdersIndividually(updates, true);
				}
			}

			log.info("주문 데이터 복구 완료 - 새 주문: {} 건, 업데이트: {} 건",
					processedNewOrders, processedUpdates);

		} catch (MatchingException e) {
			log.error("주문 데이터 복구 실패: {}", e.getMessage(), e);
		}
	}
}
