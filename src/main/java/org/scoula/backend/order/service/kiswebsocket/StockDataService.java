// package org.scoula.backend.order.service.kiswebsocket;
//
// import org.springframework.stereotype.Service;
//
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
//
// @Service
// @Slf4j
// @RequiredArgsConstructor
// public class StockDataService {
// 	private final KisWebSocketClient kisWebSocketClient;
//
// 	public void startStockDataStream(String stockCode) {
// 		try {
// 			kisWebSocketClient.connectStockData(stockCode);
// 			log.info("Started stock data stream for code: {}", stockCode);
// 		} catch (Exception e) {
// 			log.error("Failed to start stock data stream for code {}: {}", stockCode, e.getMessage());
// 			throw new RuntimeException("Failed to start stock data stream", e);
// 		}
// 	}
//
// 	public void stopStockDataStream(String stockCode) {
// 		try {
// 			kisWebSocketClient.disconnectAll();
// 			log.info("Stopped stock data stream for code: {}", stockCode);
// 		} catch (Exception e) {
// 			log.error("Failed to stop stock data stream for code {}: {}", stockCode, e.getMessage());
// 		}
// 	}
//
// 	public void startHogaDataStream(String stockCode) {
// 		try {
// 			kisWebSocketClient.connectHogaData(stockCode);
// 			log.info("Started stock data stream for code: {}", stockCode);
// 		} catch (Exception e) {
// 			log.error("Failed to start stock data stream for code {}: {}", stockCode, e.getMessage());
// 			throw new RuntimeException("Failed to start stock data stream", e);
// 		}
// 	}
//
// 	public void stopHogaDataStream(String stockCode) {
// 		try {
// 			kisWebSocketClient.disconnectAll();
// 			log.info("Stopped stock data stream for code: {}", stockCode);
// 		} catch (Exception e) {
// 			log.error("Failed to stop stock data stream for code {}: {}", stockCode, e.getMessage());
// 		}
// 	}
//
// }
