package org.scoula.backend.order.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.scoula.backend.order.controller.response.KisStockResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.dto.CandleDto;
import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.dto.ChartUpdateDto;
import org.scoula.backend.order.repository.TradeHistoryRepositoryImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 거래 내역 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class TradeHistoryService {
	private final TradeHistoryRepositoryImpl tradeHistoryRepository;
	private final SimpMessagingTemplate messagingTemplate;

	// 상수 정의
	private static final int MAX_TRADE_HISTORY = 1000; // 종목당 최대 보관 거래 수
	private static final int CANDLE_KEEP_NUMBER = 30; // 캔들 데이터 보관 개수
	private static final double DEFAULT_PRICE = 57400; // 기본 가격

	// 메모리 저장소
	private final Map<String, ConcurrentLinkedQueue<TradeHistory>> recentTradesMap = new ConcurrentHashMap<>();
	private final Map<String, List<CandleDto>> candleMap = new ConcurrentHashMap<>();

	/**
	 * 캔들 데이터 업데이트
	 */
	public void updateCandles(String symbol) {
		List<CandleDto> existingCandles = candleMap.getOrDefault(symbol, new ArrayList<>());

		long now = Instant.now().getEpochSecond();
		long currentCandleTime = now - (now % 15);

		if (existingCandles.isEmpty()) {
			// 새 캔들 생성
			CandleDto newCandle = createNewCandle(currentCandleTime, getLastPrice(symbol));
			existingCandles.add(newCandle);
		} else {
			// 이전 캔들 기반 새 캔들 생성
			CandleDto prevCandle = existingCandles.get(existingCandles.size() - 1);
			CandleDto nextCandle = createNextCandle(currentCandleTime, prevCandle.getClose());
			existingCandles.add(nextCandle);
		}

		// 캔들 목록 크기 제한
		if (existingCandles.size() > CANDLE_KEEP_NUMBER) {
			existingCandles = new ArrayList<>(
					existingCandles.subList(existingCandles.size() - CANDLE_KEEP_NUMBER, existingCandles.size())
			);
		}

		candleMap.put(symbol, existingCandles);
	}

	/**
	 * 새 캔들 생성
	 */
	private CandleDto createNewCandle(long time, double price) {
		return CandleDto.builder()
				.time(time)
				.open(price)
				.high(price)
				.low(price)
				.close(price)
				.volume(0)
				.build();
	}

	/**
	 * 이전 캔들 기반 새 캔들 생성
	 */
	private CandleDto createNextCandle(long time, double closePrice) {
		return CandleDto.builder()
				.time(time)
				.open(closePrice)
				.high(closePrice)
				.low(closePrice)
				.close(closePrice)
				.volume(0)
				.build();
	}

	/**
	 * 마지막 거래 가격 조회
	 */
	private double getLastPrice(String symbol) {
		ConcurrentLinkedQueue<TradeHistory> trades = recentTradesMap.get(symbol);
		if (trades == null || trades.isEmpty()) {
			return DEFAULT_PRICE;
		}
		return trades.peek().getPrice().doubleValue();
	}

	/**
	 * 마지막 거래 조회
	 */
	public Optional<TradeHistory> getLastTrade(String companyCode) {
		Queue<TradeHistory> trades = recentTradesMap.get(companyCode);
		return trades == null || trades.isEmpty() ?
				Optional.empty() : Optional.of(trades.peek());
	}

	/**
	 * 거래 내역 저장 (일반 사용자)
	 */
	public void saveTradeHistory(TradeHistoryResponse tradeHistoryResponse) {
		TradeHistory tradeHistory = convertToEntity(tradeHistoryResponse);

		// DB 저장
		tradeHistoryRepository.save(tradeHistory);

		// 메모리 저장 및 캔들 업데이트
		storeTradeHistory(tradeHistory);
		updateCandleWithTrade(tradeHistory);

		// 실시간 업데이트 전송
		sendChartUpdate(tradeHistory);
	}

	/**
	 * 거래 내역 저장 (KIS 데이터)
	 */
	public void sendForKI(final TradeHistoryResponse tradeHistoryResponse, final KisStockResponse KISStockResponse) {
		TradeHistory tradeHistory = convertToEntity(tradeHistoryResponse);

		// DB 저장
		tradeHistoryRepository.save(tradeHistory);

		// 메모리 저장 및 캔들 업데이트
		storeTradeHistory(tradeHistory);
		updateCandleWithTrade(tradeHistory);

		// 실시간 업데이트 전송
		List<CandleDto> candles = candleMap.get(tradeHistory.getCompanyCode());
		if (candles != null && !candles.isEmpty()) {
			CandleDto updatedCandle = candles.get(candles.size() - 1);

			ChartUpdateDto updateDto = ChartUpdateDto.builder()
					.price(updatedCandle.getClose())
					.volume(updatedCandle.getVolume())
					.build();

			messagingTemplate.convertAndSend("/topic/chart/" + tradeHistory.getCompanyCode(), updateDto);
		}
	}

	/**
	 * 거래 내역 메모리 저장
	 */
	private void storeTradeHistory(TradeHistory tradeHistory) {
		ConcurrentLinkedQueue<TradeHistory> trades =
				recentTradesMap.computeIfAbsent(tradeHistory.getCompanyCode(),
						k -> new ConcurrentLinkedQueue<>());
		trades.offer(tradeHistory);

		// 최대 개수 유지
		while (trades.size() > MAX_TRADE_HISTORY) {
			trades.poll();
		}
	}

	/**
	 * 캔들 데이터 업데이트
	 */
	private void updateCandleWithTrade(TradeHistory tradeHistory) {
		List<CandleDto> candles = candleMap.get(tradeHistory.getCompanyCode());
		if (candles != null && !candles.isEmpty()) {
			CandleDto currentCandle = candles.get(candles.size() - 1);

			// 새 캔들 생성 및 교체
			CandleDto updatedCandle = CandleDto.builder()
					.time(currentCandle.getTime())
					.open(currentCandle.getOpen())
					.high(Math.max(currentCandle.getHigh(), tradeHistory.getPrice().doubleValue()))
					.low(Math.min(currentCandle.getLow(), tradeHistory.getPrice().doubleValue()))
					.close(tradeHistory.getPrice().doubleValue())
					.volume(currentCandle.getVolume() + tradeHistory.getQuantity().intValue())
					.build();

			candles.set(candles.size() - 1, updatedCandle);
			candleMap.put(tradeHistory.getCompanyCode(), candles);
		}
	}

	/**
	 * 차트 업데이트 전송
	 */
	private void sendChartUpdate(TradeHistory tradeHistory) {
		ChartUpdateDto updateDto = ChartUpdateDto.builder()
				.price(tradeHistory.getPrice().doubleValue())
				.volume(tradeHistory.getQuantity().intValue())
				.build();
		messagingTemplate.convertAndSend("/topic/chart/" + tradeHistory.getCompanyCode(), updateDto);
	}

	/**
	 * 차트 히스토리 조회
	 */
	public ChartResponseDto getChartHistory(String symbol) {
		List<CandleDto> candles = candleMap.getOrDefault(symbol, new ArrayList<>());
		return ChartResponseDto.builder()
				.candles(new ArrayList<>(candles))  // 방어적 복사
				.build();
	}

	/**
	 * 전체 거래 내역 조회
	 */
	public List<TradeHistoryResponse> getTradeHistory() {
		List<TradeHistory> tradeHistories = tradeHistoryRepository.getTradeHistory();
		return tradeHistories.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	/**
	 * DTO를 엔티티로 변환
	 */
	private TradeHistory convertToEntity(TradeHistoryResponse dto) {
		return TradeHistory.builder()
				.id(dto.id())
				.companyCode(dto.companyCode())
				.sellOrderId(dto.sellOrderId())
				.buyOrderId(dto.buyOrderId())
				.quantity(dto.quantity())
				.price(dto.price())
				.tradeTime(dto.tradeTime())
				.build();
	}

	/**
	 * 엔티티를 DTO로 변환
	 */
	private TradeHistoryResponse convertToDto(TradeHistory entity) {
		return TradeHistoryResponse.builder()
				.id(entity.getId())
				.companyCode(entity.getCompanyCode())
				.sellOrderId(entity.getSellOrderId())
				.buyOrderId(entity.getBuyOrderId())
				.quantity(entity.getQuantity())
				.price(entity.getPrice())
				.tradeTime(entity.getTradeTime())
				.build();
	}
}
