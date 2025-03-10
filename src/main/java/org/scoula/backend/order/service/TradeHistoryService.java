package org.scoula.backend.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.scoula.backend.order.controller.response.KisStockResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.TimeFrame;
import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.dto.CandleDto;
import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.dto.ChartUpdateDto;
import org.scoula.backend.order.repository.TradeHistoryRepositoryImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 거래 내역 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeHistoryService {
	private final TradeHistoryRepositoryImpl tradeHistoryRepository;
	private final SimpMessagingTemplate messagingTemplate;

	// 상수 정의
	private static final int MAX_TRADE_HISTORY = 1000; // 종목당 최대 보관 거래 수
	private static final int CANDLE_KEEP_NUMBER = 100; // 캔들 데이터 보관 개수
	private static final double DEFAULT_PRICE = 57400; // 기본 가격

	// 메모리 저장소
	private final Map<String, ConcurrentLinkedQueue<TradeHistory>> recentTradesMap = new ConcurrentHashMap<>();
	private final Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap = new ConcurrentHashMap<>();

	// 동시성 제어를 위한 락
	private final Map<String, ReentrantReadWriteLock> companyLocks = new ConcurrentHashMap<>();

	/**
	 * TradeHistoryService에 추가할 거래 내역 로드 메서드
	 */
	@PostConstruct
	public void loadTradeHistoryFromDb() {
		log.info("서버 시작 시 DB에서 거래 내역 로드 중...");

		try {
			// 활성화된 모든 회사 코드 목록 조회 (거래 내역이 있는 회사 코드로 가정)
			// ToDo : Company로 전환 고민이 필요합니다.
			final List<String> activatedCompanyCodes = tradeHistoryRepository.findDistinctCompanyCodes();

			if (activatedCompanyCodes.isEmpty()) {
				log.info("활성화된 회사 코드가 없습니다. 거래 내역 로드를 건너뜁니다.");
				return;
			}

			// 각 회사 코드별로 최근 거래 내역 로드
			for (String companyCode : activatedCompanyCodes) {
				loadRecentTradesForCompany(companyCode);
				// 각 회사별로 락 초기화
				companyLocks.putIfAbsent(companyCode, new ReentrantReadWriteLock());
			}

			log.info("거래 내역 로드 완료");

			// 로드된 거래 내역을 기반으로 캔들 초기화
			initializeCandlesFromTrades();
		} catch (Exception e) {
			log.error("거래 내역 로드 중 오류 발생: {}", e.getMessage(), e);
		}
	}

	/**
	 * 특정 회사의 최근 거래 내역 로드
	 */
	private void loadRecentTradesForCompany(final String companyCode) {
		try {
			// 회사별 최근 거래 내역 MAX_TRADE_HISTORY 개 조회
			final List<TradeHistory> recentTrades = tradeHistoryRepository.findRecentTradesByCompanyCode(
					companyCode, MAX_TRADE_HISTORY);

			if (recentTrades.isEmpty()) {
				log.debug("{} 회사의 거래 내역이 없습니다.", companyCode);
				return;
			}

			log.debug("{} 회사의 거래 내역 {} 개 로드", companyCode, recentTrades.size());

			// 최신 거래가 먼저 오도록 정렬
			recentTrades.sort((t1, t2) -> Long.compare(t2.getTradeTime(), t1.getTradeTime()));

			// 메모리에 저장 (최신 거래가 큐의 앞에 오도록 역순으로 저장)
			final ConcurrentLinkedQueue<TradeHistory> tradeQueue = new ConcurrentLinkedQueue<>(recentTrades);
			recentTradesMap.put(companyCode, tradeQueue);

			log.debug("{} 회사의 거래 내역 로드 완료. 최신 거래 시간: {}",
					companyCode,
					recentTrades.isEmpty() ? "없음" :
							Instant.ofEpochSecond(recentTrades.get(0).getTradeTime()));

		} catch (Exception e) {
			log.error("{} 회사의 거래 내역 로드 중 오류 발생: {}", companyCode, e.getMessage(), e);
		}
	}

	/**
	 * 로드된 거래 내역을 기반으로 캔들 초기화
	 */
	private void initializeCandlesFromTrades() {
		log.info("로드된 거래 내역을 기반으로 캔들 데이터 초기화 중...");

		// 각 회사 코드별로 캔들 초기화
		for (String companyCode : recentTradesMap.keySet()) {
			// 최근 거래 내역
			final Queue<TradeHistory> trades = recentTradesMap.get(companyCode);

			if (trades == null || trades.isEmpty()) {
				continue;
			}

			log.debug("{} 회사의 캔들 데이터 초기화 중", companyCode);

			final List<TradeHistory> tradesList = new ArrayList<>(trades);
			// 시간순으로 정렬 (오래된 거래부터)
			tradesList.sort(Comparator.comparingLong(TradeHistory::getTradeTime));

			// 각 타임프레임별로 캔들 초기화
			for (TimeFrame timeFrame : TimeFrame.values()) {
				initializeTimeFrameCandles(companyCode, timeFrame, tradesList);
			}

			log.debug("{} 회사의 캔들 데이터 초기화 완료", companyCode);
		}

		log.info("캔들 데이터 초기화 완료");
	}

	/**
	 * 특정 타임프레임의 캔들 초기화
	 */
	private void initializeTimeFrameCandles(
			final String companyCode, final TimeFrame timeFrame, final List<TradeHistory> trades) {
		if (trades.isEmpty()) {
			return;
		}

		// 타임프레임별 캔들 데이터 초기화
		final Map<TimeFrame, List<CandleDto>> companyCodeCandleMap =
				timeFrameCandleMap.computeIfAbsent(companyCode, k -> new EnumMap<>(TimeFrame.class));

		// 캔들 리스트 초기화
		final List<CandleDto> candles = new ArrayList<>();
		companyCodeCandleMap.put(timeFrame, candles);

		// 캔들 생성을 위한 변수들
		final Long timeFrameSeconds = timeFrame.getSeconds();
		Long currentCandleTime = null;
		Double open = null, high = null, low = null, close = null;
		Integer volume = 0;

		// 거래 내역을 순회하며 캔들 생성
		for (TradeHistory trade : trades) {
			final Long tradeTime = trade.getTradeTime();
			final Double price = trade.getPrice().doubleValue();
			final Integer tradeVolume = trade.getQuantity().intValue();

			// 거래 시간이 속하는 캔들 시간 계산
			final Long candleTime = calculateCandleTime(tradeTime, timeFrameSeconds);

			// 첫 거래이거나 새로운 캔들 시간인 경우
			if (currentCandleTime == null || candleTime > currentCandleTime) {
				// 빈 캔들 채우기 (거래가 없는 시간대)
				if (currentCandleTime != null) {
					fillEmptyCandles(candles, currentCandleTime, candleTime, timeFrameSeconds, close);
				}

				// 이전 캔들이 있으면 추가
				if (currentCandleTime != null) {
					candles.add(createCandleDto(currentCandleTime, open, high, low, close, volume));
				}

				// 새 캔들 시작
				currentCandleTime = candleTime;
				open = high = low = close = price;
				volume = tradeVolume;
			} else {
				// 같은 캔들 시간 내의 거래인 경우 업데이트
				high = Math.max(high, price);
				low = Math.min(low, price);
				close = price; // 마지막 거래 가격이 종가
				volume += tradeVolume;
			}
		}

		// 마지막 캔들 추가
		if (currentCandleTime != null) {
			candles.add(createCandleDto(currentCandleTime, open, high, low, close, volume));
		}

		// 현재 시간까지 빈 캔들 채우기
		Long now = Instant.now().getEpochSecond();
		Long lastCandleTime = candles.isEmpty() ? null : candles.get(candles.size() - 1).time();
		if (lastCandleTime != null) {
			fillEmptyCandles(candles, lastCandleTime, calculateCandleTime(now, timeFrameSeconds), timeFrameSeconds,
					close);
		}

		// 캔들 개수 제한
		if (candles.size() > CANDLE_KEEP_NUMBER) {
			companyCodeCandleMap.put(timeFrame,
					new ArrayList<>(candles.subList(candles.size() - CANDLE_KEEP_NUMBER, candles.size())));
		}

		log.debug("{} 회사의 {} 타임프레임 캔들 {} 개 생성 완료",
				companyCode, timeFrame.getTimeCode(), candles.size());
	}

	/**
	 * 캔들 시간 계산 (타임프레임 단위로 내림)
	 */
	private Long calculateCandleTime(Long timeInSeconds, Long timeFrameSeconds) {
		return timeInSeconds - (timeInSeconds % timeFrameSeconds);
	}

	/**
	 * 빈 캔들 채우기
	 */
	private void fillEmptyCandles(
			List<CandleDto> candles, Long fromTime, Long toTime, Long timeFrameSeconds, Double lastPrice) {
		// 인자 유효성 검증 강화
		if (lastPrice == null || fromTime == null || toTime == null) {
			log.warn("빈 캔들을 채우는데 유효하지 않은 인자: fromTime={}, toTime={}, lastPrice={}",
					fromTime, toTime, lastPrice);
			return;
		}

		// fromTime과 toTime이 같거나 fromTime이 더 큰 경우 단순히 리턴 (경고 로그 없이)
		if (fromTime >= toTime) {
			log.debug("빈 캔들 채우기 건너뜀: fromTime({})이 toTime({})보다 크거나 같음",
					Instant.ofEpochSecond(fromTime), Instant.ofEpochSecond(toTime));
			return;
		}

		// 기본값 설정
		final double safePriceValue = !Double.isNaN(lastPrice) ? lastPrice : DEFAULT_PRICE;

		// 프론트엔드 디버깅을 위한 로그
		log.debug("빈 캔들 채우기: fromTime={}, toTime={}, timeFrameSeconds={}, 간격={}초",
				fromTime, toTime, timeFrameSeconds, toTime - fromTime);

		// 캔들 채우기
		for (Long time = fromTime + timeFrameSeconds; time < toTime; time += timeFrameSeconds) {
			candles.add(createCandleDto(time, safePriceValue, safePriceValue, safePriceValue, safePriceValue, 0));
		}
	}

	/**
	 * CandleDto 생성 (null 값 방지)
	 */
	private CandleDto createCandleDto(Long time, Double open, Double high, Double low, Double close, Integer volume) {
		// 시간 값이 유효하지 않은 경우 로그 남기고 현재 시간으로 대체
		if (time == null || time <= 0) {
			log.warn("유효하지 않은 candle 시간값: {}, 현재 시간으로 대체합니다.", time);
			time = Instant.now().getEpochSecond();
		}

		// 가격 값 유효성 검증 및 기본값 설정
		double safeOpen = (open != null && !open.isNaN()) ? open : DEFAULT_PRICE;
		double safeHigh = (high != null && !high.isNaN()) ? high : DEFAULT_PRICE;
		double safeLow = (low != null && !low.isNaN()) ? low : DEFAULT_PRICE;
		double safeClose = (close != null && !close.isNaN()) ? close : DEFAULT_PRICE;
		int safeVolume = (volume != null && volume >= 0) ? volume : 0;

		// 최대/최소값 논리적 검증
		if (safeHigh < safeOpen || safeHigh < safeClose || safeHigh < safeLow) {
			safeHigh = Math.max(Math.max(safeOpen, safeClose), safeLow);
		}

		if (safeLow > safeOpen || safeLow > safeClose || safeLow > safeHigh) {
			safeLow = Math.min(Math.min(safeOpen, safeClose), safeHigh);
		}

		return CandleDto.builder()
				.time(time)
				.open(safeOpen)
				.high(safeHigh)
				.low(safeLow)
				.close(safeClose)
				.volume(safeVolume)
				.build();
	}

	/**
	 * 모든 타임프레임의 캔들 데이터 업데이트
	 */
	public void updateCandles(final String companyCode) {
		if (companyCode == null || companyCode.trim().isEmpty()) {
			log.warn("캔들 업데이트 실패: 종목코드가 유효하지 않습니다.");
			return;
		}

		log.debug("종목 {}의 모든 타임프레임 캔들 업데이트 시작", companyCode);

		// 모든 타임프레임에 대해 업데이트
		for (TimeFrame timeFrame : TimeFrame.values()) {
			try {
				updateCandlesForTimeFrame(companyCode, timeFrame);
			} catch (Exception e) {
				log.error("종목 {}의 {} 타임프레임 캔들 업데이트 중 오류 발생: {}",
						companyCode, timeFrame.getTimeCode(), e.getMessage(), e);
			}
		}

		log.debug("종목 {}의 모든 타임프레임 캔들 업데이트 완료", companyCode);
	}

	/**
	 * 특정 타임프레임 캔들 데이터 업데이트
	 */
	private void updateCandlesForTimeFrame(final String companyCode, final TimeFrame timeFrame) {
		// 회사별 락 획득
		ReentrantReadWriteLock lock = companyLocks.computeIfAbsent(companyCode, k -> new ReentrantReadWriteLock());
		lock.writeLock().lock();

		try {
			final Map<TimeFrame, List<CandleDto>> companyCodeCandleMap = timeFrameCandleMap.computeIfAbsent(
					companyCode, k -> new EnumMap<>(TimeFrame.class));

			List<CandleDto> existingCandles = companyCodeCandleMap.computeIfAbsent(
					timeFrame, k -> new ArrayList<>());

			final Long now = Instant.now().getEpochSecond();
			final Long timeFrameSeconds = timeFrame.getSeconds();
			final Long currentCandleTime = calculateCandleTime(now, timeFrameSeconds);

			if (existingCandles.isEmpty()) {
				// 새 캔들 생성
				final CandleDto newCandle = createCandleDto(currentCandleTime, getLastPrice(companyCode),
						getLastPrice(companyCode), getLastPrice(companyCode), getLastPrice(companyCode), 0);
				existingCandles.add(newCandle);
			} else {
				CandleDto lastCandle = existingCandles.get(existingCandles.size() - 1);

				// 빈 캔들 채우기 전에 시간 값이 같은지 확인
				if (!lastCandle.time().equals(currentCandleTime)) {
					// 시간 값이 다른 경우에만 빈 캔들 채우기 실행
					fillEmptyCandles(existingCandles, lastCandle.time(), currentCandleTime, timeFrameSeconds,
							lastCandle.close());

					// 새 캔들 시간이 되었으면 새로운 캔들 추가
					if (currentCandleTime > lastCandle.time()) {
						CandleDto nextCandle = createCandleDto(currentCandleTime, lastCandle.close(),
								lastCandle.close(), lastCandle.close(), lastCandle.close(), 0);
						existingCandles.add(nextCandle);
					}
				} else {
					// 시간 값이 같은 경우에는 로그만 출력 (선택적)
					log.debug("새 캔들 생성 건너뜀: 현재 캔들 시간과 마지막 캔들 시간이 같음 ({})",
							Instant.ofEpochSecond(currentCandleTime));
				}
			}

			// 캔들 목록 크키 제한
			if (existingCandles.size() > CANDLE_KEEP_NUMBER) {
				List<CandleDto> limitedCandles = new ArrayList<>(
						existingCandles.subList(existingCandles.size() - CANDLE_KEEP_NUMBER, existingCandles.size())
				);
				companyCodeCandleMap.put(timeFrame, limitedCandles);
			} else {
				companyCodeCandleMap.put(timeFrame, existingCandles);
			}

			timeFrameCandleMap.put(companyCode, companyCodeCandleMap);
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * 마지막 거래 가격 조회
	 */
	private double getLastPrice(final String companyCode) {
		ConcurrentLinkedQueue<TradeHistory> trades = recentTradesMap.get(companyCode);
		if (trades == null || trades.isEmpty()) {
			return DEFAULT_PRICE;
		}
		return trades.peek().getPrice().doubleValue();
	}

	/**
	 * 마지막 거래 조회
	 */
	public Optional<TradeHistory> getLastTrade(final String companyCode) {
		Queue<TradeHistory> trades = recentTradesMap.get(companyCode);
		return trades == null || trades.isEmpty() ?
				Optional.empty() : Optional.of(trades.peek());
	}

	/**
	 * 거래 내역 저장 (일반 사용자)
	 */
	public void saveTradeHistory(final TradeHistoryResponse tradeHistoryResponse) {
		TradeHistory tradeHistory = convertToEntity(tradeHistoryResponse);

		// DB 저장
		tradeHistoryRepository.save(tradeHistory);
		log.info("거래 내역 저장: {}", tradeHistory);

		// 메모리 저장 및 캔들 업데이트
		storeTradeHistory(tradeHistory);
		updateAllTimeFrameCandles(tradeHistory);

		// 실시간 업데이트 전송
		sendChartUpdates(tradeHistory);
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
		updateAllTimeFrameCandles(tradeHistory);

		// 실시간 업데이트 전송
		sendChartUpdates(tradeHistory);
	}

	/**
	 * 거래 내역 메모리 저장
	 */
	private void storeTradeHistory(final TradeHistory tradeHistory) {
		final ConcurrentLinkedQueue<TradeHistory> trades =
				recentTradesMap.computeIfAbsent(tradeHistory.getCompanyCode(),
						k -> new ConcurrentLinkedQueue<>());
		trades.offer(tradeHistory);

		// 최대 개수 유지
		while (trades.size() > MAX_TRADE_HISTORY) {
			trades.poll();
		}
	}

	/**
	 * 모든 타임프레임 캔들 데이터 업데이트
	 */
	private void updateAllTimeFrameCandles(final TradeHistory tradeHistory) {
		final String companyCode = tradeHistory.getCompanyCode();

		// 회사별 락 획득
		ReentrantReadWriteLock lock = companyLocks.computeIfAbsent(companyCode, k -> new ReentrantReadWriteLock());
		lock.writeLock().lock();

		try {
			Map<TimeFrame, List<CandleDto>> companyCodeCandleMap =
					timeFrameCandleMap.computeIfAbsent(companyCode, k -> new EnumMap<>(TimeFrame.class));
			for (TimeFrame timeFrame : TimeFrame.values()) {
				updateCandleWithTradeForTimeFrame(tradeHistory, companyCodeCandleMap, timeFrame);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * 특정 타임프레임 캔들 데이터 업데이트
	 */
	private void updateCandleWithTradeForTimeFrame(
			final TradeHistory tradeHistory,
			final Map<TimeFrame, List<CandleDto>> companyCodeCandleMap,
			final TimeFrame timeFrame) {

		final List<CandleDto> candles = companyCodeCandleMap.computeIfAbsent(timeFrame, k -> new ArrayList<>());

		final Long tradeTime = tradeHistory.getTradeTime();
		final Long timeFrameSeconds = timeFrame.getSeconds();
		final Long candleTime = calculateCandleTime(tradeTime, timeFrameSeconds);
		final Double price = tradeHistory.getPrice() != null ? tradeHistory.getPrice().doubleValue() : DEFAULT_PRICE;
		final Integer volume = tradeHistory.getQuantity() != null ? tradeHistory.getQuantity().intValue() : 0;

		if (candles.isEmpty()) {
			// 캔들이 없으면 새로 생성
			final CandleDto newCandle = createCandleDto(candleTime, price, price, price, price, volume);
			candles.add(newCandle);
		} else {
			final CandleDto lastCandle = candles.get(candles.size() - 1);

			// 현재 캔들 시간 범위에 속하는지 확인
			if (lastCandle.time().equals(candleTime)) {
				// 기존 캔들 업데이트
				final CandleDto updatedCandle = CandleDto.builder()
						.time(lastCandle.time())
						.open(lastCandle.open())
						.high(Math.max(lastCandle.high(), price))
						.low(Math.min(lastCandle.low(), price))
						.close(price)
						.volume(lastCandle.volume() + volume)
						.build();

				candles.set(candles.size() - 1, updatedCandle);
			} else if (candleTime > lastCandle.time()) {
				// 빈 캔들 채우기
				fillEmptyCandles(candles, lastCandle.time(), candleTime, timeFrameSeconds, lastCandle.close());

				// 새 캔들 생성
				final CandleDto newCandle = createCandleDto(candleTime, price, price, price, price, volume);
				candles.add(newCandle);

				// 캔들 목록 크기 제한
				if (candles.size() > CANDLE_KEEP_NUMBER) {
					List<CandleDto> limitedCandles = new ArrayList<>(
							candles.subList(candles.size() - CANDLE_KEEP_NUMBER, candles.size())
					);
					companyCodeCandleMap.put(timeFrame, limitedCandles);
				} else {
					companyCodeCandleMap.put(timeFrame, candles);
				}
			}
		}
	}

	/**
	 * 차트 업데이트 전송
	 */
	private void sendChartUpdates(final TradeHistory tradeHistory) {
		final String companyCode = tradeHistory.getCompanyCode();
		final Double price = tradeHistory.getPrice() != null ? tradeHistory.getPrice().doubleValue() : DEFAULT_PRICE;
		final Integer volume = tradeHistory.getQuantity() != null ? tradeHistory.getQuantity().intValue() : 0;

		// 기본 업데이트 전송
		final ChartUpdateDto basicUpdateDto = ChartUpdateDto.builder()
				.price(price)
				.volume(volume)
				.build();
		messagingTemplate.convertAndSend("/topic/chart/" + companyCode, basicUpdateDto);

		// 회사별 락 획득 (읽기 락)
		ReentrantReadWriteLock lock = companyLocks.computeIfAbsent(companyCode, k -> new ReentrantReadWriteLock());
		lock.readLock().lock();

		try {
			// 각 타임프레임별 업데이트 전송
			final Map<TimeFrame, List<CandleDto>> companyCodeCandleMap = timeFrameCandleMap.get(companyCode);
			if (companyCodeCandleMap != null) {
				for (TimeFrame timeFrame : TimeFrame.values()) {
					final List<CandleDto> candles = companyCodeCandleMap.get(timeFrame);
					if (candles != null && !candles.isEmpty()) {
						final CandleDto latestCandle = candles.get(candles.size() - 1);
						if (latestCandle != null) {
							final ChartUpdateDto timeframeUpdateDto = ChartUpdateDto.builder()
									.price(latestCandle.close())
									.volume(latestCandle.volume())
									.timeCode(timeFrame.getTimeCode())
									.build();

							messagingTemplate.convertAndSend(
									"/topic/chart/" + companyCode + "/" + timeFrame.getTimeCode(),
									timeframeUpdateDto);
						}
					}
				}
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * 차트 기록 조회 메소드 개선 - 데이터 일관성 보장
	 */
	public ChartResponseDto getChartHistory(final String companyCode, final String timeframeCode) {
		TimeFrame requestedTimeFrame = null;

		// 타임프레임 코드로 찾기
		for (TimeFrame tf : TimeFrame.values()) {
			if (tf.getTimeCode().equals(timeframeCode)) {
				requestedTimeFrame = tf;
				break;
			}
		}

		// 타임프레임을 찾지 못했다면 기본값(15분) 설정
		if (requestedTimeFrame == null) {
			log.warn("요청된 타임프레임 코드 '{}'를 찾을 수 없습니다. 기본값(15분)을 사용합니다.", timeframeCode);
			requestedTimeFrame = TimeFrame.MINUTE_15;
		}

		// 종목별 락 획득 (읽기 락)
		ReentrantReadWriteLock lock = companyLocks.computeIfAbsent(companyCode, k -> new ReentrantReadWriteLock());
		lock.readLock().lock();

		try {
			List<CandleDto> candles = new ArrayList<>();

			// 필요한 캔들 데이터 가져오기
			final List<CandleDto> timeFrameCandles = getTimeFrameCandles(companyCode, requestedTimeFrame);

			// 유효한 캔들이 있으면 처리
			if (!timeFrameCandles.isEmpty()) {
				// 유효한 캔들만 필터링, 정렬 후 처리
				candles = processAndConvertValidCandles(timeFrameCandles);
			}

			// 캔들이 비어있으면 현재 시간 기준으로 더미 데이터 생성
			if (candles.isEmpty()) {
				candles.add(createDefaultCandle(requestedTimeFrame));
				log.info("종목 {}의 {}({}초) 타임프레임에 대한 캔들 데이터가 없어 기본 캔들을 생성합니다.",
						companyCode, requestedTimeFrame.getTimeCode(), requestedTimeFrame.getSeconds());
			}

			// 디버그 로깅
			logChartData(companyCode, requestedTimeFrame, candles);

			return ChartResponseDto.builder()
					.candles(candles)
					.timeCode(requestedTimeFrame.getTimeCode())
					.build();
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * 종목과 타임프레임에 맞는 캔들 데이터 조회
	 */
	private List<CandleDto> getTimeFrameCandles(String companyCode, TimeFrame timeFrame) {
		final Map<TimeFrame, List<CandleDto>> companyCodeCandleMap = timeFrameCandleMap.get(companyCode);
		if (companyCodeCandleMap == null) {
			return new ArrayList<>();
		}

		List<CandleDto> timeFrameCandles = companyCodeCandleMap.get(timeFrame);
		return timeFrameCandles != null ? timeFrameCandles : new ArrayList<>();
	}

	/**
	 * 유효한 캔들 데이터만 필터링, 정렬 후 변환
	 */
	private List<CandleDto> processAndConvertValidCandles(List<CandleDto> candles) {
		// 유효한 캔들만 필터링
		List<CandleDto> validCandles = new ArrayList<>(candles.stream()
				.filter(candle -> candle != null && candle.time() != null && candle.time() > 0)
				.toList());

		if (validCandles.isEmpty()) {
			return new ArrayList<>();
		}

		// 시간순으로 정렬
		validCandles.sort(Comparator.comparingLong(CandleDto::time));

		// 정규화된 캔들 생성
		return validCandles.stream()
				.map(candle -> createCandleDto(
						candle.time(),
						candle.open(),
						candle.high(),
						candle.low(),
						candle.close(),
						candle.volume())
				)
				.toList();
	}

	/**
	 * 기본 캔들 생성
	 */
	private CandleDto createDefaultCandle(TimeFrame timeFrame) {
		Long now = Instant.now().getEpochSecond();
		Long timeFrameSeconds = timeFrame.getSeconds();
		Long currentCandleTime = calculateCandleTime(now, timeFrameSeconds);

		return createCandleDto(
				currentCandleTime,
				DEFAULT_PRICE,
				DEFAULT_PRICE,
				DEFAULT_PRICE,
				DEFAULT_PRICE,
				0);
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

	/**
	 * 차트 데이터 로깅
	 */
	private void logChartData(String companyCode, TimeFrame timeFrame, List<CandleDto> candles) {
		log.debug("차트 데이터 반환: 종목={}, 타임프레임={}, 캔들 수={}, 첫 캔들 시간={}, 마지막 캔들 시간={}",
				companyCode, timeFrame.getTimeCode(), candles.size(),
				candles.isEmpty() ? "없음" : Instant.ofEpochSecond(candles.get(0).time()),
				candles.isEmpty() ? "없음" : Instant.ofEpochSecond(candles.get(candles.size() - 1).time()));
	}
}
