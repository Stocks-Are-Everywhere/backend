package org.scoula.backend.order.service.batch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.dto.OrderPersistentDto;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrderPersistentQueue {
	// 상수 정의
	private static final String NEW_ORDER_PREFIX = "order:";
	private static final String UPDATE_ORDER_PREFIX = "order-update:";

	private final RocksDB db;
	private final ObjectMapper objectMapper;
	private final AtomicLong sequence = new AtomicLong(0);

	public OrderPersistentQueue(RocksDB db, ObjectMapper objectMapper) {
		this.db = db;
		this.objectMapper = objectMapper;
		// 시작 시 최대 시퀀스 번호 복구
		recoverMaxSequence();
	}

	// 새 주문 큐에 추가
	public CompletableFuture<Void> enqueue(Order order) {
		return CompletableFuture.runAsync(() -> {
			try {
				OrderPersistentDto dto = new OrderPersistentDto(order);
				long seq = sequence.incrementAndGet();
				String key = NEW_ORDER_PREFIX + String.format("%019d", seq);
				byte[] value = objectMapper.writeValueAsBytes(dto);

				// RocksDB에 데이터 기록
				db.put(key.getBytes(), value);
				log.debug("주문 데이터 큐에 저장 - 주문ID: {}, 시퀀스: {}", order.getId(), seq);
			} catch (Exception e) {
				log.error("주문 데이터 큐 저장 실패: {}", e.getMessage(), e);
				throw new RuntimeException("주문 데이터 큐 저장 실패", e);
			}
		});
	}

	// 주문 업데이트 큐에 추가
	public CompletableFuture<Void> enqueueUpdate(Order order) {
		return CompletableFuture.runAsync(() -> {
			try {
				OrderPersistentDto dto = new OrderPersistentDto(order);
				long seq = sequence.incrementAndGet();
				String key = UPDATE_ORDER_PREFIX + String.format("%019d", seq);
				byte[] value = objectMapper.writeValueAsBytes(dto);

				// 업데이트 작업임을 명시하는 접두어로 키 구성
				db.put(key.getBytes(), value);
				log.debug("주문 업데이트 데이터 큐에 저장 - 주문ID: {}, 시퀀스: {}", order.getId(), seq);
			} catch (Exception e) {
				log.error("주문 업데이트 데이터 큐 저장 실패: {}", e.getMessage(), e);
				throw new RuntimeException("주문 업데이트 데이터 큐 저장 실패", e);
			}
		});
	}

	// 새 주문 배치 처리를 위해 가져오기
	public List<Order> dequeueNewOrdersForProcessing(int batchSize) {
		List<Order> orders = new ArrayList<>();
		Map<String, byte[]> keyValueMap = new HashMap<>();
		RocksIterator iterator = db.newIterator();

		try {
			iterator.seek(NEW_ORDER_PREFIX.getBytes());
			int count = 0;

			while (iterator.isValid() && count < batchSize) {
				String key = new String(iterator.key());
				if (!key.startsWith(NEW_ORDER_PREFIX)) {
					break;
				}

				byte[] value = iterator.value();
				keyValueMap.put(key, value);

				try {
					// DTO로 역직렬화 후 Entity로 변환
					OrderPersistentDto dto = objectMapper.readValue(value, OrderPersistentDto.class);
					Order order = dto.toOrderEntity();
					orders.add(order);
					count++;
				} catch (Exception e) {
					log.error("주문 데이터 역직렬화 실패: {}", e.getMessage());
					// 손상된 데이터는 건너뛰고 계속 진행
				}

				iterator.next();
			}

			log.debug("새 주문 큐에서 {} 건 로드", orders.size());
			return orders;
		} catch (Exception e) {
			log.error("새 주문 데이터 큐 조회 실패: {}", e.getMessage(), e);
			throw new RuntimeException("새 주문 데이터 큐 조회 실패", e);
		} finally {
			iterator.close();
		}
	}

	// 업데이트 주문 배치 처리를 위해 가져오기
	public List<Order> dequeueUpdatesForProcessing(int batchSize) {
		List<Order> orders = new ArrayList<>();
		Map<String, byte[]> keyValueMap = new HashMap<>();
		RocksIterator iterator = db.newIterator();

		try {
			iterator.seek(UPDATE_ORDER_PREFIX.getBytes());
			int count = 0;

			while (iterator.isValid() && count < batchSize) {
				String key = new String(iterator.key());
				if (!key.startsWith(UPDATE_ORDER_PREFIX)) {
					break;
				}

				byte[] value = iterator.value();
				keyValueMap.put(key, value);

				try {
					// DTO로 역직렬화 후 Entity로 변환
					OrderPersistentDto dto = objectMapper.readValue(value, OrderPersistentDto.class);
					Order order = dto.toOrderEntity();
					orders.add(order);
					count++;
				} catch (Exception e) {
					log.error("주문 업데이트 데이터 역직렬화 실패: {}", e.getMessage());
					// 손상된 데이터는 건너뛰고 계속 진행
				}

				iterator.next();
			}

			log.debug("업데이트 주문 큐에서 {} 건 로드", orders.size());
			return orders;
		} catch (Exception e) {
			log.error("업데이트 주문 데이터 큐 조회 실패: {}", e.getMessage(), e);
			throw new RuntimeException("업데이트 주문 데이터 큐 조회 실패", e);
		} finally {
			iterator.close();
		}
	}

	// 처리 완료된 새 주문 큐에서 제거
	public void removeProcessedNewOrders(List<Order> processedOrders) {
		if (processedOrders.isEmpty()) {
			return;
		}

		WriteBatch writeBatch = new WriteBatch();
		WriteOptions writeOptions = new WriteOptions();

		try {
			// 주문 ID로 키 맵 생성
			Map<Long, List<byte[]>> orderKeysMap = findOrderKeysByPrefix(processedOrders, NEW_ORDER_PREFIX);

			// 찾은 모든 키 삭제
			int deletedCount = 0;
			for (Order order : processedOrders) {
				List<byte[]> keys = orderKeysMap.get(order.getId());
				if (keys != null && !keys.isEmpty()) {
					for (byte[] keyBytes : keys) {
						writeBatch.delete(keyBytes);
						deletedCount++;
					}
				}
			}

			if (deletedCount > 0) {
				db.write(writeOptions, writeBatch);
				log.debug("처리 완료된 새 주문 {} 건 큐에서 제거 (키 {} 개)", processedOrders.size(), deletedCount);
			}
		} catch (Exception e) {
			log.error("주문 데이터 큐 삭제 실패: {}", e.getMessage(), e);
			throw new RuntimeException("주문 데이터 큐 삭제 실패", e);
		} finally {
			writeBatch.close();
			writeOptions.close();
		}
	}

	// 처리 완료된 업데이트 주문 큐에서 제거
	public void removeProcessedUpdates(List<Order> processedOrders) {
		if (processedOrders.isEmpty()) {
			return;
		}

		WriteBatch writeBatch = new WriteBatch();
		WriteOptions writeOptions = new WriteOptions();

		try {
			// 주문 ID로 키 맵 생성
			Map<Long, List<byte[]>> orderKeysMap = findOrderKeysByPrefix(processedOrders, UPDATE_ORDER_PREFIX);

			// 찾은 모든 키 삭제
			int deletedCount = 0;
			for (Order order : processedOrders) {
				List<byte[]> keys = orderKeysMap.get(order.getId());
				if (keys != null && !keys.isEmpty()) {
					for (byte[] keyBytes : keys) {
						writeBatch.delete(keyBytes);
						deletedCount++;
					}
				}
			}

			if (deletedCount > 0) {
				db.write(writeOptions, writeBatch);
				log.debug("처리 완료된 업데이트 주문 {} 건 큐에서 제거 (키 {} 개)", processedOrders.size(), deletedCount);
			}
		} catch (Exception e) {
			log.error("업데이트 주문 데이터 큐 삭제 실패: {}", e.getMessage(), e);
			throw new RuntimeException("업데이트 주문 데이터 큐 삭제 실패", e);
		} finally {
			writeBatch.close();
			writeOptions.close();
		}
	}

	// DB에 없는 주문 ID 리스트 큐에서 제거
	public void removeNonExistentUpdates(List<Long> nonExistentOrderIds) {
		if (nonExistentOrderIds.isEmpty()) {
			return;
		}

		WriteBatch writeBatch = new WriteBatch();
		WriteOptions writeOptions = new WriteOptions();

		try {
			// ID별 키 찾기
			Map<Long, List<byte[]>> idKeysMap = new HashMap<>();
			for (Long orderId : nonExistentOrderIds) {
				List<byte[]> keys = findKeysByOrderId(orderId, UPDATE_ORDER_PREFIX);
				if (!keys.isEmpty()) {
					idKeysMap.put(orderId, keys);
				}
			}

			// 찾은 모든 키 삭제
			int deletedCount = 0;
			for (List<byte[]> keys : idKeysMap.values()) {
				for (byte[] keyBytes : keys) {
					writeBatch.delete(keyBytes);
					deletedCount++;
				}
			}

			if (deletedCount > 0) {
				db.write(writeOptions, writeBatch);
				log.debug("DB에 없는 주문 {} 건 큐에서 제거 (키 {} 개)", nonExistentOrderIds.size(), deletedCount);
			}
		} catch (Exception e) {
			log.error("존재하지 않는 주문 큐 삭제 실패: {}", e.getMessage(), e);
			throw new RuntimeException("존재하지 않는 주문 큐 삭제 실패", e);
		} finally {
			writeBatch.close();
			writeOptions.close();
		}
	}

	// 특정 프리픽스로 시작하는 키 중 주문 ID 기준으로 키 찾기
	private Map<Long, List<byte[]>> findOrderKeysByPrefix(List<Order> orders, String prefix) {
		Map<Long, List<byte[]>> result = new HashMap<>();
		RocksIterator iterator = db.newIterator();

		try {
			// ID별 키 리스트 초기화
			for (Order order : orders) {
				result.put(order.getId(), new ArrayList<>());
			}

			// 프리픽스로 시작하는 모든 키 스캔
			iterator.seek(prefix.getBytes());
			while (iterator.isValid()) {
				String key = new String(iterator.key());
				if (!key.startsWith(prefix)) {
					break;
				}

				try {
					byte[] value = iterator.value();
					OrderPersistentDto dto = objectMapper.readValue(value, OrderPersistentDto.class);

					// 주문 ID가 목록에 있으면 키 추가
					if (result.containsKey(dto.id())) {
						result.get(dto.id()).add(iterator.key());
					}
				} catch (Exception e) {
					log.warn("주문 역직렬화 실패 (키 검색 중): {}", e.getMessage());
				}

				iterator.next();
			}

			return result;
		} finally {
			iterator.close();
		}
	}

	// 특정 주문 ID와 프리픽스로 키 찾기
	private List<byte[]> findKeysByOrderId(Long orderId, String prefix) {
		List<byte[]> keys = new ArrayList<>();
		RocksIterator iterator = db.newIterator();

		try {
			iterator.seek(prefix.getBytes());
			while (iterator.isValid()) {
				String key = new String(iterator.key());
				if (!key.startsWith(prefix)) {
					break;
				}

				try {
					byte[] value = iterator.value();
					OrderPersistentDto dto = objectMapper.readValue(value, OrderPersistentDto.class);

					if (dto.id().equals(orderId)) {
						keys.add(iterator.key());
					}
				} catch (Exception e) {
					log.warn("주문 역직렬화 실패 (ID 검색 중): {}", e.getMessage());
				}

				iterator.next();
			}

			return keys;
		} finally {
			iterator.close();
		}
	}

	// 시작 시 최대 시퀀스 번호 복구
	private void recoverMaxSequence() {
		RocksIterator iterator = db.newIterator();

		try {
			// 마지막 항목으로 이동
			iterator.seekToLast();

			if (iterator.isValid()) {
				String key = new String(iterator.key());
				// 키에서 접두어 제거 후 숫자 추출
				int prefixEndIndex = Math.max(key.lastIndexOf(':') + 1, 0);
				if (prefixEndIndex < key.length()) {
					try {
						long maxSeq = Long.parseLong(key.substring(prefixEndIndex));
						sequence.set(maxSeq);
						log.info("최대 시퀀스 번호 복구: {}", maxSeq);
					} catch (NumberFormatException e) {
						log.warn("시퀀스 번호 파싱 실패: {}", key);
					}
				}
			}
		} finally {
			iterator.close();
		}
	}
}
