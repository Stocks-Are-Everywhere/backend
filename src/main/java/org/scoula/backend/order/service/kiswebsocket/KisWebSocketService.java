package org.scoula.backend.order.service.kiswebsocket;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.json.simple.JSONObject;
import org.scoula.backend.order.controller.response.KisStockResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
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
public class KisWebSocketService {
	// 상수 정의
	private static final String KIS_WS_URL = "ws://ops.koreainvestment.com:31000/tryitout/H0STCNT0";
	private static final String APPROVAL_KEY = "";
	private static final String TR_ID = "H0STCNT0";
	private static final String CUSTOMER_TYPE = "P";
	private static final String TR_TYPE = "1";
	private static final String CONTENT_TYPE = "utf-8";

	private WebSocketClient client;
	private WebSocketSession session;

	private final SimpMessagingTemplate messagingTemplate;
	private final TradeHistoryService tradeHistoryService;

	/**
	 * 주식 코드 웹소켓 연결
	 */
	public void connect(String stockCode) {
		client = new StandardWebSocketClient();

		try {
			client.execute(createWebSocketHandler(stockCode), new WebSocketHttpHeaders(), URI.create(KIS_WS_URL));
		} catch (Exception e) {
			log.error("Failed to connect to KIS WebSocket server", e);
			throw new RuntimeException("WebSocket connection failed", e);
		}
	}

	/**
	 * 웹소켓 연결 종료
	 */
	public void disconnect() {
		if (session != null && session.isOpen()) {
			try {
				session.close();
				log.info("WebSocket connection closed");
			} catch (IOException e) {
				log.error("Error closing WebSocket connection", e);
			}
		}
	}

	/**
	 * 웹소켓 핸들러 생성
	 */
	private WebSocketHandler createWebSocketHandler(String stockCode) {
		return new WebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				log.info("Connected to KIS WebSocket server");
				KisWebSocketService.this.session = session;
				sendSubscribeMessage(session, stockCode);
			}

			@Override
			public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
				try {
					String payload = (String)message.getPayload();
					log.info("Received message: {}", payload);

					// 연결 확인 응답 메시지 체크
					if (payload.startsWith("{")) {
						log.info("Received connection response");
						return;
					}

					processStockData(payload, stockCode);
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
			}

			@Override
			public boolean supportsPartialMessages() {
				return false;
			}
		};
	}

	/**
	 * 주식 데이터 처리
	 */
	private void processStockData(String payload, String stockCode) {
		try {
			final KisStockResponse kisStockResponse = parseKisData(payload);
			final TradeHistoryResponse response = TradeHistoryResponse.builder()
				.companyCode(stockCode)
				.sellOrderId(1L)
				.buyOrderId(2L)
				.price(BigDecimal.valueOf(kisStockResponse.getCurrentPrice()))
				.quantity(BigDecimal.valueOf(kisStockResponse.getAccVolume()))
				.tradeTime(kisStockResponse.getTime())
				.build();

			tradeHistoryService.sendForKI(response, kisStockResponse);
		} catch (Exception e) {
			log.error("Error processing stock data: {}", e.getMessage(), e);
		}
	}

	/**
	 * 구독 메시지 전송
	 */
	private void sendSubscribeMessage(WebSocketSession session, String stockCode) throws IOException {
		JSONObject request = createSubscribeRequest(stockCode);
		session.sendMessage(new TextMessage(request.toString()));
		log.debug("Sent subscribe message for stock code: {}", stockCode);
	}

	/**
	 * 구독 요청 JSON 생성
	 */
	private JSONObject createSubscribeRequest(String stockCode) {
		JSONObject header = new JSONObject();
		header.put("approval_key", APPROVAL_KEY);
		header.put("custtype", CUSTOMER_TYPE);
		header.put("tr_type", TR_TYPE);
		header.put("content-type", CONTENT_TYPE);

		JSONObject input = new JSONObject();
		input.put("tr_id", TR_ID);
		input.put("tr_key", stockCode);

		JSONObject body = new JSONObject();
		body.put("input", input);

		JSONObject request = new JSONObject();
		request.put("header", header);
		request.put("body", body);

		return request;
	}

	/**
	 * KIS 원시 데이터 파싱
	 */
	private KisStockResponse parseKisData(String rawData) {
		String[] sections = rawData.split("\\|");
		if (sections.length < 4) {
			log.error("Invalid data format: {}", rawData);
			throw new IllegalArgumentException("Invalid data format");
		}

		String[] fields = sections[3].split("\\^");
		KisStockResponse data = new KisStockResponse();

		try {
			// 시간 정보 설정
			data.setTime(parseDateTime(fields[1]));

			// 가격 및 거래량 정보 설정
			setNumericData(data, fields);

			return data;
		} catch (ParseException | NumberFormatException e) {
			log.error("Failed to parse numeric data: {} - {}", e.getMessage(), rawData);
			throw new RuntimeException("Data parsing failed", e);
		}
	}

	/**
	 * 시간 문자열 파싱
	 */
	private LocalDateTime parseDateTime(String timeStr) {
		LocalDate today = LocalDate.now();

		int hour = Integer.parseInt(timeStr.substring(0, 2));
		int minute = Integer.parseInt(timeStr.substring(2, 4));
		int second = Integer.parseInt(timeStr.substring(4, 6));

		return LocalDateTime.of(
			today.getYear(),
			today.getMonth(),
			today.getDayOfMonth(),
			hour,
			minute,
			second
		);
	}

	/**
	 * 숫자 데이터 파싱 및 설정
	 */
	private void setNumericData(KisStockResponse data, String[] fields) throws ParseException {
		DecimalFormat df = new DecimalFormat("#.##");
		df.setParseBigDecimal(true);

		// 가격 정보 설정
		data.setCurrentPrice(df.parse(fields[2]).doubleValue());
		data.setChangePrice(df.parse(fields[4]).doubleValue());
		data.setChangeRate(df.parse(fields[5]).doubleValue());
		data.setOpenPrice(df.parse(fields[7]).doubleValue());
		data.setHighPrice(df.parse(fields[8]).doubleValue());
		data.setLowPrice(df.parse(fields[9]).doubleValue());

		// 거래량 정보 설정
		data.setVolume(Long.parseLong(fields[12]));
		data.setAccVolume(Long.parseLong(fields[13]));
	}

}
