package org.scoula.backend.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Profile("test") // 테스트 프로필에서만 활성화
public class TestThreadingConfig {

	@Bean(name = "orderProcessingExecutor")
	public Executor orderProcessingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2); // 테스트용으로는 작은 사이즈
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("test-order-processor-");
		executor.initialize();
		return executor;
	}

	@Bean(name = "supportTasksExecutor")
	public Executor supportTasksExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("test-support-task-");
		executor.initialize();
		return executor;
	}

	@Bean(name = "dbOperationsExecutor")
	public Executor dbOperationsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("test-db-ops-");
		executor.initialize();
		return executor;
	}
}
