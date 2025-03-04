package org.scoula.backend.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.order.controller.response.KisStockResponse;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.dto.CandleDto;
import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.dto.ChartUpdateDto;
import org.scoula.backend.order.repository.TradeHistoryRepositoryImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeHistoryService 테스트")
class TradeHistoryServiceTest {

	@Mock
	private TradeHistoryRepositoryImpl tradeHistoryRepository;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@InjectMocks
	private TradeHistoryService tradeHistoryService;

	@Captor
	private ArgumentCaptor<TradeHistory> tradeHistoryCaptor;

	@Captor
	private ArgumentCaptor<ChartUpdateDto> chartUpdateDtoCaptor;

	@Captor
	private ArgumentCaptor<String> topicCaptor;

	private static final String TEST_SYMBOL = "005930"; // Samsung Electronics code
	private static final String SECONDARY_SYMBOL = "035420"; // Naver code
	private static final String NON_EXISTENT_SYMBOL = "NONEXIST";
	private static final double DEFAULT_PRICE = 60000.0;
	private static final double HIGH_PRICE = 61000.0;
	private static final double LOW_PRICE = 59500.0;
	private static final double CLOSE_PRICE = 60500.0;
	private static final int DEFAULT_VOLUME = 100;
	private static final int CANDLE_KEEP_NUMBER = 30; // TradeHistoryService에 정의된 값과 일치해야 함
	private static final int MAX_TRADE_HISTORY = 1000; // TradeHistoryService에 정의된 값과 일치해야 함

	// 내부 맵 필드에 접근하기 위한 참조 변수
	private Map<String, ConcurrentLinkedQueue<TradeHistory>> recentTradesMap;
	private Map<String, List<CandleDto>> candleMap;

	@BeforeEach
	void setUp() {
		// 내부 컬렉션을 테스트를 위해 초기화 (가변 맵 사용)
		recentTradesMap = new ConcurrentHashMap<>();
		candleMap = new ConcurrentHashMap<>();

		ReflectionTestUtils.setField(tradeHistoryService, "recentTradesMap", recentTradesMap);
		ReflectionTestUtils.setField(tradeHistoryService, "candleMap", candleMap);
	}

	// TC14.1 일반 케이스 테스트
	@Nested
	@DisplayName("일반 케이스 - 캔들 데이터 생성 및 관리")
	class NormalCaseTests {

		// TC14.1.1 - 새 캔들 생성
		@Test
		@DisplayName("TC14.1.1 - 지정된 시간 간격으로 새 캔들 생성")
		void createNewCandleTest() {
			// given - 초기 조건: 캔들 데이터 존재
			List<CandleDto> initialCandles = new ArrayList<>();
			long now = Instant.now().getEpochSecond();
			long candleTime = now - (now % 15);

			CandleDto initialCandle = CandleDto.builder()
					.time(candleTime - 15) // 이전 캔들
					.open(DEFAULT_PRICE)
					.high(HIGH_PRICE)
					.low(LOW_PRICE)
					.close(CLOSE_PRICE)
					.volume(DEFAULT_VOLUME)
					.build();
			initialCandles.add(initialCandle);
			candleMap.put(TEST_SYMBOL, initialCandles);

			// when - 새 캔들 생성
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// then - 예상 결과: 정확한 시간에 새 캔들 생성, 초기값 설정
			List<CandleDto> candles = candleMap.get(TEST_SYMBOL);
			assertThat(candles).hasSize(2);

			CandleDto newCandle = candles.get(1);
			assertThat(newCandle.getTime()).isEqualTo(candleTime);
			assertThat(newCandle.getOpen()).isEqualTo(CLOSE_PRICE); // 이전 캔들의 종가가 시가가 됨
			assertThat(newCandle.getHigh()).isEqualTo(CLOSE_PRICE);
			assertThat(newCandle.getLow()).isEqualTo(CLOSE_PRICE);
			assertThat(newCandle.getClose()).isEqualTo(CLOSE_PRICE);
			assertThat(newCandle.getVolume()).isEqualTo(0);
		}

		// TC14.1.2 - 거래 기반 캔들 업데이트
		@Test
		@DisplayName("TC14.1.2 - 거래 발생 시 현재 캔들 데이터 업데이트")
		void updateCandleBasedOnTradeTest() {
			// given - 초기 조건: 활성 캔들 존재, 거래 발생
			List<CandleDto> candles = new ArrayList<>();
			long now = Instant.now().getEpochSecond();
			long candleTime = now - (now % 15);

			CandleDto activeCandle = CandleDto.builder()
					.time(candleTime)
					.open(DEFAULT_PRICE)
					.high(DEFAULT_PRICE)
					.low(DEFAULT_PRICE)
					.close(DEFAULT_PRICE)
					.volume(0)
					.build();
			candles.add(activeCandle);
			candleMap.put(TEST_SYMBOL, candles);

			// 거래 데이터 생성
			TradeHistoryResponse trade = TradeHistoryResponse.builder()
					.companyCode(TEST_SYMBOL)
					.quantity(BigDecimal.valueOf(50))
					.price(BigDecimal.valueOf(HIGH_PRICE)) // 61000, 새로운 고가
					.tradeTime(LocalDateTime.now())
					.build();

			TradeHistoryResponse trade2 = TradeHistoryResponse.builder()
					.companyCode(TEST_SYMBOL)
					.quantity(BigDecimal.valueOf(30))
					.price(BigDecimal.valueOf(LOW_PRICE)) // 59500, 새로운 저가
					.tradeTime(LocalDateTime.now().plusSeconds(5))
					.build();

			// when - 거래 발생에 따른 캔들 업데이트
			tradeHistoryService.saveTradeHistory(trade);
			tradeHistoryService.saveTradeHistory(trade2);

			// then - 예상 결과: 고가/저가/종가/거래량 정확히 업데이트
			List<CandleDto> updatedCandles = candleMap.get(TEST_SYMBOL);
			CandleDto updatedCandle = updatedCandles.get(0);

			assertThat(updatedCandle.getOpen()).isEqualTo(DEFAULT_PRICE);
			assertThat(updatedCandle.getHigh()).isEqualTo(HIGH_PRICE); // 최고가 업데이트
			assertThat(updatedCandle.getLow()).isEqualTo(LOW_PRICE);  // 최저가 업데이트
			assertThat(updatedCandle.getClose()).isEqualTo(LOW_PRICE); // 마지막 거래가 종가
			assertThat(updatedCandle.getVolume()).isEqualTo(80); // 총 거래량
		}

		// TC14.1.3 - 다양한 시간대 캔들 생성 (시뮬레이션)
		@Test
		@DisplayName("TC14.1.3 - 다양한 시간대 캔들 생성 및 관리")
		void multipleCandleTimeframesTest() {
			// given - 초기 조건: 충분한 거래 데이터 존재
			// 15초 간격으로 30개의 캔들 생성 시뮬레이션
			long baseTime = Instant.now().getEpochSecond();

			// 여러 캔들 생성
			for (int i = 0; i < 10; i++) {
				tradeHistoryService.updateCandles(TEST_SYMBOL);

				// 각 캔들에 거래 추가
				TradeHistoryResponse trade = TradeHistoryResponse.builder()
						.companyCode(TEST_SYMBOL)
						.quantity(BigDecimal.valueOf(10 + i))
						.price(BigDecimal.valueOf(DEFAULT_PRICE + (i * 100))) // 가격 변동
						.tradeTime(LocalDateTime.now().plusSeconds(i * 15))
						.build();

				tradeHistoryService.saveTradeHistory(trade);
			}

			// when & then - 예상 결과: 각 시간대별 캔들 정확히 생성 및 관리
			List<CandleDto> resultCandles = candleMap.get(TEST_SYMBOL);

			// 캔들 수 확인
			assertThat(resultCandles).hasSize(10);

			// 캔들 시간 간격 검증
			for (int i = 1; i < resultCandles.size(); i++) {
				CandleDto prevCandle = resultCandles.get(i - 1);
				CandleDto currentCandle = resultCandles.get(i);

				// 캔들 시간 간격이 일정한지 확인
				assertThat(currentCandle.getTime() - prevCandle.getTime()).isEqualTo(15);

				// 이전 종가와 현재 시가가 같은지 확인
				assertThat(currentCandle.getOpen()).isEqualTo(prevCandle.getClose());
			}

			// 마지막 캔들의 가격과 거래량 확인
			CandleDto lastCandle = resultCandles.get(resultCandles.size() - 1);
			assertThat(lastCandle.getClose()).isEqualTo(DEFAULT_PRICE + 900); // 마지막 거래 가격
			assertThat(lastCandle.getVolume()).isEqualTo(19); // 마지막 거래량
		}
	}

	// TC14.2 예외 케이스 테스트
	@Nested
	@DisplayName("예외 케이스 - 비정상 상황 처리")
	class ExceptionCaseTests {

		// TC14.2.1 - 거래 없는 기간 캔들
		@Test
		@DisplayName("TC14.2.1 - 거래가 없는 시간대의 캔들 처리")
		void noCandleTradesPeriodTest() {
			// given - 초기 조건: 거래 없는 기간 존재
			List<CandleDto> candles = new ArrayList<>();
			long now = Instant.now().getEpochSecond();
			long candleTime = now - (now % 15);

			// 초기 캔들 생성
			CandleDto initialCandle = CandleDto.builder()
					.time(candleTime - 15)
					.open(DEFAULT_PRICE)
					.high(HIGH_PRICE)
					.low(LOW_PRICE)
					.close(CLOSE_PRICE) // 마지막 종가
					.volume(DEFAULT_VOLUME)
					.build();
			candles.add(initialCandle);
			candleMap.put(TEST_SYMBOL, candles);

			// when - 거래 없이 새 캔들 생성
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// then - 예상 결과: 이전 종가 유지, 거래량 0으로 캔들 생성
			List<CandleDto> updatedCandles = candleMap.get(TEST_SYMBOL);
			assertThat(updatedCandles).hasSize(2);

			CandleDto newCandle = updatedCandles.get(1);
			assertThat(newCandle.getOpen()).isEqualTo(CLOSE_PRICE); // 이전 종가가 시가로 설정
			assertThat(newCandle.getHigh()).isEqualTo(CLOSE_PRICE);
			assertThat(newCandle.getLow()).isEqualTo(CLOSE_PRICE);
			assertThat(newCandle.getClose()).isEqualTo(CLOSE_PRICE); // 종가도 유지
			assertThat(newCandle.getVolume()).isEqualTo(0); // 거래량은 0
		}

		// TC14.2.2 - 시스템 중단 후 캔들 복구 (시뮬레이션)
		@Test
		@DisplayName("TC14.2.2 - 시스템 중단 후 캔들 데이터 처리")
		void systemRecoveryAfterInterruptionTest() {
			// given - 초기 조건: 시스템 재시작 상태 (마지막 캔들이 오래된 상태)
			List<CandleDto> candles = new ArrayList<>();
			long now = Instant.now().getEpochSecond();
			long oldCandleTime = now - 3600; // 1시간 전 캔들

			CandleDto oldCandle = CandleDto.builder()
					.time(oldCandleTime)
					.open(DEFAULT_PRICE)
					.high(HIGH_PRICE)
					.low(LOW_PRICE)
					.close(CLOSE_PRICE)
					.volume(DEFAULT_VOLUME)
					.build();
			candles.add(oldCandle);
			candleMap.put(TEST_SYMBOL, candles);

			// 거래 데이터 저장 (최신 거래)
			ConcurrentLinkedQueue<TradeHistory> trades = new ConcurrentLinkedQueue<>();
			TradeHistory recentTrade = TradeHistory.builder()
					.companyCode(TEST_SYMBOL)
					.price(BigDecimal.valueOf(62000)) // 새로운 가격
					.quantity(BigDecimal.valueOf(50))
					.tradeTime(LocalDateTime.now().minusMinutes(5)) // 5분 전 거래
					.build();
			trades.offer(recentTrade);
			recentTradesMap.put(TEST_SYMBOL, trades);

			// when - 시스템 재시작 후 캔들 업데이트
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// then - 예상 결과: 누락된 캔들 식별 및 가능한 데이터로 복구
			List<CandleDto> updatedCandles = candleMap.get(TEST_SYMBOL);
			assertThat(updatedCandles).hasSize(2); // 새 캔들 하나 추가

			CandleDto newCandle = updatedCandles.get(1);
			assertThat(newCandle.getTime()).isGreaterThan(oldCandleTime);
			// 최근 거래가 있으므로 그 가격 기반으로 설정됨
			assertThat(newCandle.getOpen()).isEqualTo(CLOSE_PRICE); // 이전 캔들의 종가
			assertThat(newCandle.getClose()).isEqualTo(CLOSE_PRICE); // 아직 거래가 반영되기 전
		}

		// TC14.2.3 - 비정상 거래 데이터 처리
		@Test
		@DisplayName("TC14.2.3 - 이상치(극단적 가격)가 포함된 거래 처리")
		void abnormalTradeDataHandlingTest() {
			// given - 초기 조건: 정상 캔들 데이터가 있는 상태
			List<CandleDto> candles = new ArrayList<>();
			long now = Instant.now().getEpochSecond();
			long candleTime = now - (now % 15);

			CandleDto normalCandle = CandleDto.builder()
					.time(candleTime)
					.open(DEFAULT_PRICE)
					.high(DEFAULT_PRICE)
					.low(DEFAULT_PRICE)
					.close(DEFAULT_PRICE)
					.volume(0)
					.build();
			candles.add(normalCandle);
			candleMap.put(TEST_SYMBOL, candles);

			// 극단적인 가격의 거래 데이터 생성 (정상 가격의 2배)
			TradeHistoryResponse extremeTrade = TradeHistoryResponse.builder()
					.companyCode(TEST_SYMBOL)
					.quantity(BigDecimal.valueOf(10))
					.price(BigDecimal.valueOf(DEFAULT_PRICE * 2)) // 극단적인 가격
					.tradeTime(LocalDateTime.now())
					.build();

			// when - 이상 거래 데이터 처리
			tradeHistoryService.saveTradeHistory(extremeTrade);

			// then - 예상 결과: 이상치도 정상 처리 (현재 서비스는 이상치 필터링 없음)
			List<CandleDto> updatedCandles = candleMap.get(TEST_SYMBOL);
			CandleDto updatedCandle = updatedCandles.get(0);

			// 서비스가 이상치를 필터링하지 않으므로 값이 그대로 반영됨
			assertThat(updatedCandle.getHigh()).isEqualTo(DEFAULT_PRICE * 2);
			assertThat(updatedCandle.getClose()).isEqualTo(DEFAULT_PRICE * 2);
			assertThat(updatedCandle.getVolume()).isEqualTo(10);
		}
	}

	// TC14.3 엣지 케이스 테스트
	@Nested
	@DisplayName("엣지 케이스 - 극한 상황 처리")
	class EdgeCaseTests {

		// TC14.3.1 - 대량 거래 기반 캔들 생성
		@Test
		@DisplayName("TC14.3.1 - 대량 거래 기반 캔들 생성")
		void highFrequencyTradesTest() {
			// given - 초기 조건: 고빈도 거래 환경
			List<CandleDto> candles = new ArrayList<>();
			long now = Instant.now().getEpochSecond();
			long candleTime = now - (now % 15);

			CandleDto initialCandle = CandleDto.builder()
					.time(candleTime)
					.open(DEFAULT_PRICE)
					.high(DEFAULT_PRICE)
					.low(DEFAULT_PRICE)
					.close(DEFAULT_PRICE)
					.volume(0)
					.build();
			candles.add(initialCandle);
			candleMap.put(TEST_SYMBOL, candles);

			// 대량의 거래 데이터 생성 (100건)
			List<TradeHistoryResponse> highFrequencyTrades = IntStream.range(0, 100)
					.mapToObj(i -> {
						double price = DEFAULT_PRICE + (i % 20) * 10 - 100; // 가격 변동
						return TradeHistoryResponse.builder()
								.companyCode(TEST_SYMBOL)
								.quantity(BigDecimal.valueOf(1 + (i % 5)))
								.price(BigDecimal.valueOf(price))
								.tradeTime(LocalDateTime.now().plusNanos(i * 1_000_000)) // 밀리초 간격
								.build();
					})
					.collect(Collectors.toList());

			// when - 대량 거래 처리
			for (TradeHistoryResponse trade : highFrequencyTrades) {
				tradeHistoryService.saveTradeHistory(trade);
			}

			// then - 예상 결과: 모든 거래가 정확히 반영됨
			List<CandleDto> updatedCandles = candleMap.get(TEST_SYMBOL);
			CandleDto updatedCandle = updatedCandles.get(0);

			// 최소/최대 가격 확인
			double expectedMin = DEFAULT_PRICE - 100;
			double expectedMax = DEFAULT_PRICE + 190;

			assertThat(updatedCandle.getLow()).isEqualTo(expectedMin);
			assertThat(updatedCandle.getHigh()).isEqualTo(expectedMax);
			// 마지막 거래의 가격이 종가가 됨
			assertThat(updatedCandle.getClose()).isEqualTo(DEFAULT_PRICE + 90);

			// 총 거래량 확인 (1~5 사이의 수량)
			int expectedTotalVolume = highFrequencyTrades.stream()
					.mapToInt(t -> t.quantity().intValue())
					.sum();
			assertThat(updatedCandle.getVolume()).isEqualTo(expectedTotalVolume);
		}

		// TC14.3.2 - 장기간 캔들 데이터 관리 (시뮬레이션)
		@Test
		@DisplayName("TC14.3.2 - 장기간 캔들 데이터 관리")
		void longTermCandleDataManagementTest() {
			// given - 초기 조건: 대량의 히스토리 데이터
			// 50개의 캔들 생성 (CANDLE_KEEP_NUMBER = 30 초과)
			long now = Instant.now().getEpochSecond();
			List<CandleDto> historicalCandles = IntStream.range(0, 50)
					.mapToObj(i -> {
						long time = now - ((50 - i) * 15);
						return CandleDto.builder()
								.time(time)
								.open(DEFAULT_PRICE - i)
								.high(DEFAULT_PRICE + 100 - i)
								.low(DEFAULT_PRICE - 100 - i)
								.close(DEFAULT_PRICE + 50 - i)
								.volume(100 + i)
								.build();
					})
					.collect(Collectors.toList());
			candleMap.put(TEST_SYMBOL, historicalCandles);

			// when - 추가 캔들 생성 시도
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// then - 예상 결과: 최대 개수만 유지됨
			List<CandleDto> managedCandles = candleMap.get(TEST_SYMBOL);

			// CANDLE_KEEP_NUMBER = 30개만 유지되는지 확인
			assertThat(managedCandles).hasSize(CANDLE_KEEP_NUMBER);

			// 가장 최근 데이터가 유지되는지 확인
			long oldestCandleTime = managedCandles.get(0).getTime();
			long newestCandleTime = managedCandles.get(managedCandles.size() - 1).getTime();

			// 가장 오래된 캔들과 최신 캔들의 시간 차이가 (CANDLE_KEEP_NUMBER-1) * 15초인지 확인
			assertThat(newestCandleTime - oldestCandleTime).isEqualTo((CANDLE_KEEP_NUMBER - 1) * 15);
		}

		// TC14.3.3 - 시간대 경계 거래 처리
		@Test
		@DisplayName("TC14.3.3 - 캔들 시간 경계에 발생한 거래 처리")
		void tradeCandleBoundaryTest() {
			// given - 초기 조건: 캔들 전환 시점의 거래
			long now = Instant.now().getEpochSecond();
			long currentCandleTime = now - (now % 15);

			// 첫 번째 캔들 생성
			List<CandleDto> candles = new ArrayList<>();
			CandleDto firstCandle = CandleDto.builder()
					.time(currentCandleTime - 15) // 이전 캔들
					.open(DEFAULT_PRICE)
					.high(DEFAULT_PRICE)
					.low(DEFAULT_PRICE)
					.close(DEFAULT_PRICE)
					.volume(0)
					.build();
			candles.add(firstCandle);
			candleMap.put(TEST_SYMBOL, candles);

			// 두 번째 캔들 생성
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// 경계 시점 거래 데이터 (두 번째 캔들 경계)
			TradeHistoryResponse boundaryTrade = TradeHistoryResponse.builder()
					.companyCode(TEST_SYMBOL)
					.quantity(BigDecimal.valueOf(50))
					.price(BigDecimal.valueOf(HIGH_PRICE))
					.tradeTime(LocalDateTime.now()) // 현재 시간 (두 번째 캔들 시간대)
					.build();

			// when - 경계 시점 거래 처리
			tradeHistoryService.saveTradeHistory(boundaryTrade);

			// 세 번째 캔들 생성
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// then - 예상 결과: 정확한 캔들에 거래 할당
			List<CandleDto> updatedCandles = candleMap.get(TEST_SYMBOL);
			assertThat(updatedCandles).hasSize(3);

			// 두 번째 캔들에 거래가 반영되었는지 확인
			CandleDto secondCandle = updatedCandles.get(1);
			assertThat(secondCandle.getHigh()).isEqualTo(HIGH_PRICE);
			assertThat(secondCandle.getClose()).isEqualTo(HIGH_PRICE);
			assertThat(secondCandle.getVolume()).isEqualTo(50);

			// 세 번째 캔들은 두 번째 캔들의 종가를 시가로 사용
			CandleDto thirdCandle = updatedCandles.get(2);
			assertThat(thirdCandle.getOpen()).isEqualTo(HIGH_PRICE);
			assertThat(thirdCandle.getVolume()).isEqualTo(0); // 아직 거래 없음
		}

		// TC14.3.4 - 캔들 데이터 제한 관리
		@Test
		@DisplayName("TC14.3.4 - 최대 보관 개수 초과 시 캔들 관리")
		void candleDataLimitManagementTest() {
			// given - 초기 조건: 최대치에 근접한 캔들 데이터
			// 정확히 CANDLE_KEEP_NUMBER개의 캔들 생성
			long now = Instant.now().getEpochSecond();
			List<CandleDto> existingCandles = IntStream.range(0, CANDLE_KEEP_NUMBER)
					.mapToObj(i -> {
						long time = now - ((CANDLE_KEEP_NUMBER - i) * 15);
						return CandleDto.builder()
								.time(time)
								.open(DEFAULT_PRICE)
								.high(DEFAULT_PRICE + 100)
								.low(DEFAULT_PRICE - 100)
								.close(DEFAULT_PRICE + 50)
								.volume(100)
								.build();
					})
					.collect(Collectors.toList());
			candleMap.put(TEST_SYMBOL, existingCandles);

			// when - 추가 캔들 생성
			tradeHistoryService.updateCandles(TEST_SYMBOL);

			// then - 예상 결과: 가장 오래된 데이터 제거, 메모리 효율성 유지
			List<CandleDto> resultCandles = candleMap.get(TEST_SYMBOL);

			// 여전히 CANDLE_KEEP_NUMBER개만 유지
			assertThat(resultCandles).hasSize(CANDLE_KEEP_NUMBER);

			// 가장 오래된 캔들이 제거되었는지 확인
			long oldestTimeInOriginal = existingCandles.get(0).getTime();
			long oldestTimeInResult = resultCandles.get(0).getTime();

			// 결과의 가장 오래된 캔들은 원본의 두 번째 캔들이어야 함
			assertThat(oldestTimeInResult).isGreaterThan(oldestTimeInOriginal);

			// 새 캔들이 마지막에 추가되었는지 확인
			CandleDto newestCandle = resultCandles.get(resultCandles.size() - 1);
			assertThat(newestCandle.getTime()).isGreaterThan(existingCandles.get(existingCandles.size() - 1).getTime());
		}

		@Test
		@DisplayName("최대 거래내역 개수 초과 시 거래 기록 관리")
		void tradeHistoryLimitManagementTest() {
			// given - 초기 조건: 최대치의 거래 기록
			ConcurrentLinkedQueue<TradeHistory> trades = new ConcurrentLinkedQueue<>();

			// MAX_TRADE_HISTORY개의 거래 추가
			for (int i = 0; i < MAX_TRADE_HISTORY; i++) {
				TradeHistory trade = TradeHistory.builder()
						.id((long)i)
						.companyCode(TEST_SYMBOL)
						.price(BigDecimal.valueOf(DEFAULT_PRICE + i))
						.quantity(BigDecimal.valueOf(10))
						.tradeTime(LocalDateTime.now().minusSeconds(MAX_TRADE_HISTORY - i))
						.build();
				trades.offer(trade);
			}
			recentTradesMap.put(TEST_SYMBOL, trades);

			// 새 거래 생성
			TradeHistoryResponse newTrade = TradeHistoryResponse.builder()
					.id((long)MAX_TRADE_HISTORY)
					.companyCode(TEST_SYMBOL)
					.price(BigDecimal.valueOf(DEFAULT_PRICE + 5000))
					.quantity(BigDecimal.valueOf(100))
					.tradeTime(LocalDateTime.now())
					.build();

			// when - 추가 거래 저장
			tradeHistoryService.saveTradeHistory(newTrade);

			// then - 예상 결과: 가장 오래된 거래 제거, 최대 개수 유지
			ConcurrentLinkedQueue<TradeHistory> resultTrades = recentTradesMap.get(TEST_SYMBOL);

			// 여전히 MAX_TRADE_HISTORY개만 유지
			assertThat(resultTrades).hasSize(MAX_TRADE_HISTORY);

			// 가장 오래된 거래(ID=0)가 제거되고 가장 최근 거래(ID=MAX_TRADE_HISTORY)가 추가되었는지 확인
			boolean hasOldestTrade = resultTrades.stream().anyMatch(t -> t.getId() == 0);
			boolean hasNewestTrade = resultTrades.stream().anyMatch(t -> t.getId() == MAX_TRADE_HISTORY);

			assertThat(hasOldestTrade).isFalse();
			assertThat(hasNewestTrade).isTrue();
		}
	}

	@Nested
	@DisplayName("거래 내역 조회 및 변환 테스트")
	class TradeHistoryRetrievalTests {

		@Test
		@DisplayName("최근 거래 조회 - 거래 있음")
		void getLastTradeTest() {
			// given
			TradeHistory trade = createTradeHistory(TEST_SYMBOL, DEFAULT_PRICE);
			ConcurrentLinkedQueue<TradeHistory> trades = new ConcurrentLinkedQueue<>();
			trades.offer(trade);
			recentTradesMap.put(TEST_SYMBOL, trades);

			// when
			Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade(TEST_SYMBOL);

			// then
			assertThat(lastTrade).isPresent();
			assertThat(lastTrade.get().getPrice()).isEqualTo(BigDecimal.valueOf(DEFAULT_PRICE));
		}

		@Test
		@DisplayName("최근 거래 조회 - 거래 없음")
		void getLastTradeForNonExistingSymbolTest() {
			// when
			Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade(NON_EXISTENT_SYMBOL);

			// then
			assertThat(lastTrade).isEmpty();
		}

		@Test
		@DisplayName("차트 히스토리 조회")
		void getChartHistoryTest() {
			// given
			List<CandleDto> candles = createSampleCandles();
			candleMap.put(TEST_SYMBOL, candles);

			// when
			ChartResponseDto response = tradeHistoryService.getChartHistory(TEST_SYMBOL);

			// then
			assertThat(response.getCandles())
					.isNotNull()
					.hasSize(1)
					.isNotSameAs(candles); // 방어적 복사 확인

			CandleDto responseCandle = response.getCandles().get(0);
			assertThat(responseCandle.getClose()).isEqualTo(CLOSE_PRICE);
		}

		@Test
		@DisplayName("전체 거래 내역 조회")
		void getTradeHistoryTest() {
			// given
			List<TradeHistory> mockTradeHistories = new ArrayList<>();

			TradeHistory trade1 = TradeHistory.builder()
					.id(1L)
					.companyCode(TEST_SYMBOL)
					.price(BigDecimal.valueOf(DEFAULT_PRICE))
					.quantity(BigDecimal.TEN)
					.tradeTime(LocalDateTime.now())
					.build();

			TradeHistory trade2 = TradeHistory.builder()
					.id(2L)
					.companyCode(SECONDARY_SYMBOL)
					.price(BigDecimal.valueOf(250000))
					.quantity(BigDecimal.valueOf(5))
					.tradeTime(LocalDateTime.now())
					.build();

			mockTradeHistories.add(trade1);
			mockTradeHistories.add(trade2);

			when(tradeHistoryRepository.getTradeHistory()).thenReturn(mockTradeHistories);

			// when
			List<TradeHistoryResponse> responses = tradeHistoryService.getTradeHistory();

			// then
			assertThat(responses).hasSize(2);
			assertThat(responses.get(0).companyCode()).isEqualTo(TEST_SYMBOL);
			assertThat(responses.get(1).companyCode()).isEqualTo(SECONDARY_SYMBOL);
		}
	}

	@Nested
	@DisplayName("거래 내역 저장 테스트")
	class SaveTradeHistoryTests {

		private TradeHistoryResponse createTradeHistoryResponse() {
			return TradeHistoryResponse.builder()
					.id(1L)
					.companyCode(TEST_SYMBOL)
					.sellOrderId(100L)
					.buyOrderId(200L)
					.quantity(BigDecimal.TEN)
					.price(BigDecimal.valueOf(DEFAULT_PRICE))
					.tradeTime(LocalDateTime.now())
					.build();
		}

		@BeforeEach
		void setUpCandles() {
			// 각 테스트 전에 캔들 데이터 설정
			List<CandleDto> candles = createSampleCandles();
			candleMap.put(TEST_SYMBOL, candles);
		}

		@Test
		@DisplayName("거래 내역 저장 및 웹소켓 전송")
		void saveTradeHistoryTest() {
			// given
			TradeHistoryResponse response = createTradeHistoryResponse();

			// when
			tradeHistoryService.saveTradeHistory(response);

			// then
			// 1. 저장소에 저장 확인
			verify(tradeHistoryRepository, times(1)).save(tradeHistoryCaptor.capture());
			TradeHistory savedTrade = tradeHistoryCaptor.getValue();
			assertThat(savedTrade.getCompanyCode()).isEqualTo(TEST_SYMBOL);

			// 2. 웹소켓 메시지 전송 확인
			verify(messagingTemplate, times(1)).convertAndSend(
					topicCaptor.capture(),
					chartUpdateDtoCaptor.capture()
			);
			assertThat(topicCaptor.getValue()).isEqualTo("/topic/chart/" + TEST_SYMBOL);
		}

		@Test
		@DisplayName("KIS 데이터 처리 및 웹소켓 전송")
		void sendForKITest() {
			// given
			TradeHistoryResponse response = createTradeHistoryResponse();
			KisStockResponse kisResponse = mock(KisStockResponse.class);

			// when
			tradeHistoryService.sendForKI(response, kisResponse);

			// then
			// 저장소에 저장 확인
			verify(tradeHistoryRepository, times(1)).save(any(TradeHistory.class));

			// 웹소켓 메시지 전송 확인
			verify(messagingTemplate, times(1)).convertAndSend(
					eq("/topic/chart/" + TEST_SYMBOL),
					any(ChartUpdateDto.class)
			);
		}

		@Test
		@DisplayName("다중 종목 거래 처리")
		void multiSymbolTradeProcessingTest() {
			// given
			// 첫 번째 종목 캔들 설정은 이미 완료 (setUpCandles)

			// 두 번째 종목 캔들 설정
			List<CandleDto> secondSymbolCandles = createSampleCandles();
			candleMap.put(SECONDARY_SYMBOL, secondSymbolCandles);

			// 두 종목의 거래 생성
			TradeHistoryResponse trade1 = createTradeHistoryResponse(); // TEST_SYMBOL

			TradeHistoryResponse trade2 = TradeHistoryResponse.builder()
					.id(2L)
					.companyCode(SECONDARY_SYMBOL)
					.sellOrderId(300L)
					.buyOrderId(400L)
					.quantity(BigDecimal.valueOf(5))
					.price(BigDecimal.valueOf(250000))
					.tradeTime(LocalDateTime.now())
					.build();

			// when - 두 종목 거래 처리
			tradeHistoryService.saveTradeHistory(trade1);
			tradeHistoryService.saveTradeHistory(trade2);

			// then
			// 두 종목 모두에 대해 저장소 저장 및 웹소켓 메시지 전송이 이루어져야 함
			verify(tradeHistoryRepository, times(2)).save(any(TradeHistory.class));

			// 두 종목에 대한 웹소켓 메시지 확인
			verify(messagingTemplate, times(1)).convertAndSend(
					eq("/topic/chart/" + TEST_SYMBOL),
					any(ChartUpdateDto.class)
			);

			verify(messagingTemplate, times(1)).convertAndSend(
					eq("/topic/chart/" + SECONDARY_SYMBOL),
					any(ChartUpdateDto.class)
			);

			// 두 종목 모두 메모리에 저장되었는지 확인
			assertThat(recentTradesMap).containsKeys(TEST_SYMBOL, SECONDARY_SYMBOL);
		}
	}

	// 테스트 헬퍼 메서드

	private TradeHistory createTradeHistory(String companyCode, double price) {
		return TradeHistory.builder()
				.companyCode(companyCode)
				.price(BigDecimal.valueOf(price))
				.quantity(BigDecimal.TEN)
				.tradeTime(LocalDateTime.now())
				.build();
	}

	private List<CandleDto> createSampleCandles() {
		List<CandleDto> candles = new ArrayList<>();
		long now = Instant.now().getEpochSecond();
		CandleDto candle = CandleDto.builder()
				.time(now - (now % 15))
				.open(DEFAULT_PRICE)
				.high(HIGH_PRICE)
				.low(LOW_PRICE)
				.close(CLOSE_PRICE)
				.volume(DEFAULT_VOLUME)
				.build();
		candles.add(candle);
		return candles;
	}
}
