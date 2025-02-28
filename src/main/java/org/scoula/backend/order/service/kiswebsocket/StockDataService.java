package org.scoula.backend.order.service.kiswebsocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.scoula.backend.order.service.TradeHistoryService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockDataService {
	private final KisWebSocketClient kisWebSocketClient;
	private Map<String, Boolean> activeSubscriptions = new ConcurrentHashMap<>();

	private final TradeHistoryService tradeHistoryService;

	public void startStockDataStream(String stockCode) {
		if (!activeSubscriptions.containsKey(stockCode)) {
			try {
				kisWebSocketClient.connect(stockCode);
				activeSubscriptions.put(stockCode, true);
				log.info("Started stock data stream for code: {}", stockCode);
			} catch (Exception e) {
				log.error("Failed to start stock data stream for code {}: {}", stockCode, e.getMessage());
				throw new RuntimeException("Failed to start stock data stream", e);
			}
		}
	}

	public void stopStockDataStream(String stockCode) {
		if (activeSubscriptions.containsKey(stockCode)) {
			try {
				kisWebSocketClient.disconnect();
				activeSubscriptions.remove(stockCode);
				log.info("Stopped stock data stream for code: {}", stockCode);
			} catch (Exception e) {
				log.error("Failed to stop stock data stream for code {}: {}", stockCode, e.getMessage());
			}
		}
	}

	public boolean isStreamActive(String stockCode) {
		return activeSubscriptions.getOrDefault(stockCode, false);
	}
}
