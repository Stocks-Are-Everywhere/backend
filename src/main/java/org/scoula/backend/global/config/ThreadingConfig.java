package org.scoula.backend.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadingConfig {
	// 주문 처리용 스레드 풀
	@Bean
	public Executor orderProcessingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor(); // 장점: 대기열 크기 제한
		executor.setCorePoolSize(10);
		executor.setMaxPoolSize(20);
		executor.setQueueCapacity(30);
		executor.setKeepAliveSeconds(60);
		executor.setThreadNamePrefix("order-processor-");

		// 스레드 풀이 가득 차면 호출한 스레드에서 작업을 실행하도록 백프레셔 제공
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		executor.setAwaitTerminationSeconds(10);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.initialize();
		return executor;
	}

	// 보조 작업용 스레드 풀 (웹소켓, 로깅 등)
	@Bean
	public Executor supportTasksExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);    // 2코어는 보조 작업에 할당
		executor.setMaxPoolSize(4);        // 필요시 최대 4코어까지 사용
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("support-task-");
		executor.initialize();
		return executor;
	}

	// 비동기 DB 작업용 스레드 풀
	@Bean
	public Executor dbOperationsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(8);    // 적절한 JDBC 연결 수 유지
		executor.setMaxPoolSize(12);
		executor.setQueueCapacity(20);
		executor.setKeepAliveSeconds(30);
		executor.setThreadNamePrefix("db-ops-");

		// 스레드 풀이 가득 차면 호출한 스레드에서 작업을 실행하도록 백프레셔 제공
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		executor.initialize();
		return executor;
	}
}
