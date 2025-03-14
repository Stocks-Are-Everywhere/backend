package org.scoula.backend.global.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.scoula.backend.order.service.batch.OrderPersistentQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class PersistentQueueConfig {

	@Bean
	public RocksDB orderQueue() throws RocksDBException, IOException {
		// RocksDB 옵션 설정
		Options options = new Options()
				.setCreateIfMissing(true)
				.setWriteBufferSize(64 * 1024 * 1024) // 64MB
				.setMaxWriteBufferNumber(3)
				.setMaxBackgroundCompactions(10);

		// 데이터 디렉토리 설정
		String dataDir = System.getProperty("user.dir") + "/data/order-queue";
		Files.createDirectories(Paths.get(dataDir));

		return RocksDB.open(options, dataDir);
	}

	@Bean
	public OrderPersistentQueue orderPersistentQueue(
			RocksDB rocksDB,
			ObjectMapper objectMapper) {
		return new OrderPersistentQueue(rocksDB, objectMapper);
	}
}
