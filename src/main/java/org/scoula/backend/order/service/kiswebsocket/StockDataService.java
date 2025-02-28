package org.scoula.backend.order.service.kiswebsocket;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockDataService {
	private final KisWebSocketService kisWebSocketService;

	public void startStockDataStream(String stockCode) {
		try {
			kisWebSocketService.connect(stockCode);
			log.info("Started stock data stream for code: {}", stockCode);
		} catch (Exception e) {
			log.error("Failed to start stock data stream for code {}: {}", stockCode, e.getMessage());
			throw new RuntimeException("Failed to start stock data stream", e);
		}
	}

	public void stopStockDataStream(String stockCode) {
		try {
			kisWebSocketService.disconnect();
			log.info("Stopped stock data stream for code: {}", stockCode);
		} catch (Exception e) {
			log.error("Failed to stop stock data stream for code {}: {}", stockCode, e.getMessage());
		}
	}

}
