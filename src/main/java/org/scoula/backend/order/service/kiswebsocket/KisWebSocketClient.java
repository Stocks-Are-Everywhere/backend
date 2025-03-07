package org.scoula.backend.order.service.kiswebsocket;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONObject;
import org.scoula.backend.order.controller.request.OrderRequest;
import org.scoula.backend.order.controller.response.KisStockResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.KisStockHogaDto;
import org.scoula.backend.order.service.OrderService;
import org.scoula.backend.order.service.TradeHistoryService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisWebSocketClient {
	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

	private static final String KIS_STOCK_URL = "ws://ops.koreainvestment.com:31000/tryitout/H0STCNT0";
	private static final String KIS_HOGA_URL = "ws://ops.koreainvestment.com:31000/tryitout/H0STASP0";

	// @Value("${ki.appSecret}")
	private final String APPROVAL_KEY_1 = "";

	private final String APPROVAL_KEY_2 = "";

	private final SimpMessagingTemplate messagingTemplate;
	private final TradeHistoryService tradeHistoryService;
	private final OrderService orderService;

	/**
	 * 주식 데이터 WebSocket 연결
	 */
	public void connectStockData(String stockCode) {
		connect(APPROVAL_KEY_1, stockCode, KIS_STOCK_URL, "H0STCNT0", this::handleStockDataMessage);
	}

	/**
	 * 호가 데이터 WebSocket 연결
	 */
	public void connectHogaData(String stockCode) {
		connect(APPROVAL_KEY_2, stockCode, KIS_HOGA_URL, "H0STASP0", this::handleHogaDataMessage);
	}

	/**
	 * WebSocket 연결 공통 메서드
	 */
	private void connect(String key, String stockCode, String url, String trId, MessageHandler messageHandler) {
		WebSocketClient client = new StandardWebSocketClient();
		String sessionKey = generateSessionKey(trId, stockCode);

		WebSocketHandler handler = new WebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				log.info("Connected to KIS WebSocket server: {} for stock {}", url, stockCode);
				sessions.put(sessionKey, session);
				sendSubscribeMessage(key, session, stockCode, trId);
			}

			@Override
			public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
				try {
					String payload = (String)message.getPayload();
					log.info(payload);

					// 연결 확인 응답 메시지인지 확인
					if (payload.startsWith("{")) {
						log.info("Received connection response: {}", payload);
						return;
					}

					// 메시지 핸들러에 위임
					messageHandler.handle(stockCode, payload);
				} catch (Exception e) {
					log.error("Error handling message: {}", e.getMessage(), e);
				}
			}

			@Override
			public void handleTransportError(WebSocketSession session, Throwable exception) {
				log.error("Transport error: ", exception);
			}

			@Override
			public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
				log.info("Connection closed: {}", closeStatus);
				sessions.remove(sessionKey);

				// 1009 에러(메시지 크기 초과) 또는 다른 연결 문제 발생 시 재연결
				if (closeStatus.getCode() == 1009 || closeStatus.getCode() != 1000) {
					log.info("Connection closed with code {}, scheduling reconnect", closeStatus.getCode());
					scheduleReconnect(key, stockCode, url, trId, messageHandler);
				}
			}

			@Override
			public boolean supportsPartialMessages() {
				return false;
			}
		};

		try {
			client.execute(handler, new WebSocketHttpHeaders(), URI.create(url));
		} catch (Exception e) {
			log.error("Failed to connect to KIS WebSocket server: {}", url, e);
			throw new RuntimeException("WebSocket connection failed", e);
		}
	}

	/**
	 * 세션 키 생성
	 */
	private String generateSessionKey(String trId, String stockCode) {
		return trId + "_" + stockCode;
	}

	/**
	 * 재연결 스케줄링
	 */
	private void scheduleReconnect(String key, String stockCode, String url, String trId,
			MessageHandler messageHandler) {
		log.info("재연결 진행, 5초 후 시도");
		try {
			Thread.sleep(5000); // 5초 대기
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		connect(key, stockCode, url, trId, messageHandler);
	}

	/**
	 * 구독 메시지 전송
	 */
	private void sendSubscribeMessage(String key, WebSocketSession session, String stockCode, String trId) throws
			IOException {
		JSONObject request = createSubscribeRequest(key, stockCode, trId);
		session.sendMessage(new TextMessage(request.toString()));
	}

	/**
	 * 구독 요청 생성
	 */
	private JSONObject createSubscribeRequest(String key, String stockCode, String trId) {
		JSONObject header = new JSONObject();
		header.put("approval_key", key);
		header.put("custtype", "P");
		header.put("tr_type", "1");
		header.put("content-type", "utf-8");

		JSONObject input = new JSONObject();
		input.put("tr_id", trId);
		input.put("tr_key", stockCode);

		JSONObject body = new JSONObject();
		body.put("input", input);

		JSONObject request = new JSONObject();
		request.put("header", header);
		request.put("body", body);

		// 전송할 메시지 형식 로깅
		log.info("Subscribe request message: {}", request.toString());

		return request;
	}

	/**
	 * 연결 해제
	 */
	public void disconnect(String trId, String stockCode) {
		String sessionKey = generateSessionKey(trId, stockCode);
		WebSocketSession session = sessions.get(sessionKey);

		if (session != null && session.isOpen()) {
			try {
				session.close();
				sessions.remove(sessionKey);
				log.info("WebSocket connection closed for {} {}", trId, stockCode);
			} catch (IOException e) {
				log.error("Error closing WebSocket connection", e);
			}
		}
	}

	/**
	 * 모든 연결 해제
	 */
	public void disconnectAll() {
		for (WebSocketSession session : sessions.values()) {
			if (session.isOpen()) {
				try {
					session.close();
					log.info("WebSocket connection closed");
				} catch (IOException e) {
					log.error("Error closing WebSocket connection", e);
				}
			}
		}
		sessions.clear();
	}

	/**
	 * 주식 데이터 메시지 처리
	 */
	private void handleStockDataMessage(String stockCode, String payload) {
		try {
			final KisStockResponse stockData = parseKisData(payload);
			final TradeHistoryResponse response = TradeHistoryResponse.builder()
					.companyCode(stockCode)
					.sellOrderId(1L)
					.buyOrderId(2L)
					.price(BigDecimal.valueOf(stockData.getCurrentPrice()))
					.quantity(BigDecimal.valueOf(stockData.getAccVolume()))
					.tradeTime(stockData.getTime())
					.build();

			tradeHistoryService.sendForKI(response, stockData);
		} catch (Exception e) {
			log.error("Error handling stock data message: {}", e.getMessage());
		}
	}

	/**
	 * 호가 데이터 메시지 처리
	 */
	private void handleHogaDataMessage(String stockCode, String payload) {
		try {
			final KisStockHogaDto stockData = parseKisHogaData(payload);

			// 매도 호가
			for (int i = 0; i < 10; i++) {
				final BigDecimal price = stockData.askPrices().get(i);
				final BigDecimal quantity = stockData.askRemains().get(i);

				final OrderRequest request = OrderRequest.builder()
						.companyCode(stockData.stockCode())
						.type(Type.SELL)
						.totalQuantity(quantity)
						.remainingQuantity(quantity)
						.status(OrderStatus.ACTIVE)
						.price(price)
						.accountId(1L)
						.build();

//				orderService.placeOrder(request);
			}

			// 매수 호가
			for (int i = 0; i < 10; i++) {
				final BigDecimal price = stockData.bidPrices().get(i);
				final BigDecimal quantity = stockData.bidRemains().get(i);

				final OrderRequest request = OrderRequest.builder()
						.companyCode(stockData.stockCode())
						.type(Type.BUY)
						.totalQuantity(quantity)
						.remainingQuantity(quantity)
						.status(OrderStatus.ACTIVE)
						.price(price)
						.accountId(1L)
						.build();

//				orderService.placeOrder(request);
			}
		} catch (Exception e) {
			log.error("Error handling hoga data message: {}", e.getMessage());
		}
	}

	/**
	 * 주식 데이터 파싱
	 */
	private KisStockResponse parseKisData(String rawData) {
		try {
			String[] sections = rawData.split("\\|");
			if (sections.length < 4) {
				log.error("잘못된 데이터 형식: {}", rawData);
				throw new IllegalArgumentException("잘못된 데이터 형식");
			}

			String[] fields = sections[3].split("\\^");
			KisStockResponse data = new KisStockResponse();

			// 기본 정보 설정
			try {
				// 날짜 정보 파싱
				// 현재 날짜 가져오기 (체결 시간은 당일 데이터만 제공)
				LocalDate today = LocalDate.now();

				// 시간 파싱
				String hour = fields[1].substring(0, 2);
				String minute = fields[1].substring(2, 4);
				String second = fields[1].substring(4, 6);

				// LocalDateTime 생성
				LocalDateTime time = LocalDateTime.of(
						today.getYear(),
						today.getMonth(),
						today.getDayOfMonth(),
						Integer.parseInt(hour),
						Integer.parseInt(minute),
						Integer.parseInt(second)
				);

				data.setTime(time.toEpochSecond(ZoneOffset.UTC));

				// 숫자 데이터 파싱 시 DecimalFormat 사용
				DecimalFormat df = new DecimalFormat("#.##");
				df.setParseBigDecimal(true);

				// 가격 정보 설정
				data.setCurrentPrice(df.parse(fields[2]).doubleValue());
				data.setChangePrice(df.parse(fields[4]).doubleValue());
				data.setChangeRate(df.parse(fields[5]).doubleValue());
				data.setOpenPrice(df.parse(fields[7]).doubleValue());
				data.setHighPrice(df.parse(fields[8]).doubleValue());
				data.setLowPrice(df.parse(fields[9]).doubleValue());

				// 거래량 정보 설정 (정수 값)
				data.setVolume(Long.parseLong(fields[12]));
				data.setAccVolume(Long.parseLong(fields[13]));

				return data;
			} catch (ParseException | NumberFormatException e) {
				log.error("숫자 파싱 실패: {} - {}", e.getMessage(), rawData);
				throw new RuntimeException("데이터 파싱 실패", e);
			}
		} catch (Exception e) {
			log.error("데이터 처리 실패: {} - {}", e.getMessage(), rawData);
			throw e;
		}
	}

	/**
	 * 호가 데이터 파싱
	 */
	private KisStockHogaDto parseKisHogaData(String rawData) {
		try {
			String[] sections = rawData.split("\\|");
			if (sections.length < 4) {
				log.error("잘못된 데이터 형식: {}", rawData);
				throw new IllegalArgumentException("잘못된 데이터 형식");
			}

			String[] fields = sections[3].split("\\^");

			// 매도호가(ASKP) 설정 (1-10)
			List<BigDecimal> askPrices = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				askPrices.add(new BigDecimal(fields[3 + i]));
			}

			// 매수호가(BIDP) 설정 (1-10)
			List<BigDecimal> bidPrices = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				bidPrices.add(new BigDecimal(fields[13 + i]));
			}

			// 매도호가 잔량(ASKP_RSQN) 설정 (1-10)
			List<BigDecimal> askRemains = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				askRemains.add(new BigDecimal(fields[23 + i]));
			}

			// 매수호가 잔량(BIDP_RSQN) 설정 (1-10)
			List<BigDecimal> bidRemains = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				bidRemains.add(new BigDecimal(fields[33 + i]));
			}

			return KisStockHogaDto.builder()
					.stockCode(fields[0])
					.businessHour(fields[1])
					.hourClassCode(fields[2])
					.askPrices(askPrices)
					.bidPrices(bidPrices)
					.askRemains(askRemains)
					.bidRemains(bidRemains)
					.totalAskRemain(parseLong(fields[43]))
					.totalBidRemain(parseLong(fields[44]))
					.overtimeTotalAskRemain(parseLong(fields[45]))
					.overtimeTotalBidRemain(parseLong(fields[46]))
					.anticipatedPrice(parseDouble(fields[47]))
					.anticipatedQuantity(parseLong(fields[48]))
					.anticipatedVolume(parseLong(fields[49]))
					.anticipatedCompared(parseDouble(fields[50]))
					.anticipatedComparedSign(fields[51])
					.anticipatedComparedRate(parseDouble(fields[52]))
					.accumulatedVolume(parseLong(fields[53]))
					.totalAskRemainChange(parseLong(fields[54]))
					.totalBidRemainChange(parseLong(fields[55]))
					.overtimeTotalAskRemainChange(parseLong(fields[56]))
					.overtimeTotalBidRemainChange(parseLong(fields[57]))
					.build();

		} catch (Exception e) {
			log.error("호가 데이터 처리 실패: {} - {}", e.getMessage(), rawData);
			throw new RuntimeException("호가 데이터 파싱 실패", e);
		}
	}

	// Double 파싱 유틸리티 함수 - 빈 문자열이나 null 처리
	private Double parseDouble(String value) {
		if (value == null || value.trim().isEmpty()) {
			return 0.0;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			log.warn("숫자 변환 실패: {}", value);
			return 0.0;
		}
	}

	// Long 파싱 유틸리티 함수 - 빈 문자열이나 null 처리
	private Long parseLong(String value) {
		if (value == null || value.trim().isEmpty()) {
			return 0L;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			log.warn("숫자 변환 실패: {}", value);
			return 0L;
		}
	}

	/**
	 * 메시지 핸들러 인터페이스
	 */
	@FunctionalInterface
	private interface MessageHandler {
		void handle(String stockCode, String payload);
	}
}
