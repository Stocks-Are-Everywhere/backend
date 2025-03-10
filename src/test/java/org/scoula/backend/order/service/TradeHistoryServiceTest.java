package org.scoula.backend.order.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.order.controller.response.TradeHistoryResponse;
import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.domain.OrderStatus;
import org.scoula.backend.order.domain.TimeFrame;
import org.scoula.backend.order.domain.TradeHistory;
import org.scoula.backend.order.domain.Type;
import org.scoula.backend.order.dto.CandleDto;
import org.scoula.backend.order.dto.ChartResponseDto;
import org.scoula.backend.order.dto.ChartUpdateDto;
import org.scoula.backend.order.repository.TradeHistoryRepositoryImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * TradeHistoryService 클래스에 대한 단위 테스트
 */

@ExtendWith(MockitoExtension.class)
class TradeHistoryServiceTest {

	@Mock
	private TradeHistoryRepository tradeHistoryRepository;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@InjectMocks
	private TradeHistoryService tradeHistoryService;

	@Mock
	private OrderRepository orderRepository;

	@Captor
	private ArgumentCaptor<String> topicCaptor;

	@Captor
	private ArgumentCaptor<ChartUpdateDto> updateDtoCaptor;

	@Captor
	private ArgumentCaptor<TradeHistory> tradeHistoryCaptor;

	// 테스트에 사용할 상수들
	private static final String TEST_COMPANY_CODE = "SAMPLE";
	private static final double DEFAULT_PRICE = 57400.0; // 기본 가격
	private static final int MAX_TRADE_HISTORY = 1000; // 종목당 최대 보관 거래 수
	private static final int CANDLE_KEEP_NUMBER = 100; // 캔들 데이터 보관 개수

	private final Order sellOrder = createOrder(101L, Type.SELL);
	private final Order buyOrder = createOrder(201L, Type.BUY);

	@Nested
	@TestMethodOrder(MethodOrderer.DisplayName.class)
	@DisplayName("7. 캔들 데이터 성성 및 관리 테스트")
	class MultipleTimeFrameCandleTests {

		@Test
		@DisplayName("TC7.1.1 거래 내역 저장 및 메모리/캔들 업데이트")
		void saveTradeHistory_ShouldStoreTradeAndUpdateCandles() {
			// Given
			TradeHistoryResponse tradeResponse = createMockTradeHistoryResponse(TEST_COMPANY_CODE);
			when(orderRepository.findById(101L)).thenReturn(Optional.of(sellOrder));
			when(orderRepository.findById(201L)).thenReturn(Optional.of(buyOrder));

			// When
			tradeHistoryService.saveTradeHistory(tradeResponse);

			// Then
			// 1. DB 저장 확인
			verify(tradeHistoryRepository).save(tradeHistoryCaptor.capture());
			TradeHistory savedTrade = tradeHistoryCaptor.getValue();
			assertEquals(TEST_COMPANY_CODE, savedTrade.getCompanyCode());
			assertEquals(tradeResponse.price(), savedTrade.getPrice());

			// 2. 메시지 전송 확인 - 여러 TimeFrame에 대해 메시지가 전송되므로 atLeastOnce() 사용
			verify(messagingTemplate, atLeastOnce()).convertAndSend(topicCaptor.capture(), updateDtoCaptor.capture());

			// 기본 메시지 토픽이 포함되어 있는지 확인
			boolean hasBasicTopic = topicCaptor.getAllValues().stream()
					.anyMatch(topic -> topic.equals("/topic/chart/" + TEST_COMPANY_CODE));
			assertTrue(hasBasicTopic, "기본 차트 토픽으로 메시지가 전송되어야 합니다");

			// 가격 정보가 올바른지 확인 (어떤 메시지든 가격은 동일해야 함)
			boolean hasPriceMatch = updateDtoCaptor.getAllValues().stream()
					.anyMatch(dto -> dto.price() == tradeResponse.price().doubleValue());
			assertTrue(hasPriceMatch, "거래 가격 정보가 메시지에 포함되어야 합니다");
		}

		@Test
		@DisplayName("TC7.1.2 동일 거래의 여러 타임프레임 반영 확인")
		void shouldReflectTradeInMultipleTimeFrames() {
			// Given: 테스트 거래 생성
			TradeHistoryResponse trade = createMockTradeHistoryResponse(TEST_COMPANY_CODE);
			when(orderRepository.findById(101L)).thenReturn(Optional.of(sellOrder));
			when(orderRepository.findById(201L)).thenReturn(Optional.of(buyOrder));

			// When: 거래 저장 및 캔들 업데이트
			tradeHistoryService.saveTradeHistory(trade);

			// Then: 각 타임프레임 캔들 확인
			Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap =
					getPrivateField(tradeHistoryService, "timeFrameCandleMap");

			Map<TimeFrame, List<CandleDto>> companyCandles = timeFrameCandleMap.get(TEST_COMPANY_CODE);
			assertNotNull(companyCandles, "종목의 캔들 맵이 존재해야 합니다");

			// 모든 타임프레임에 대해 동일한 거래가 반영되었는지 확인
			for (TimeFrame timeFrame : TimeFrame.values()) {
				List<CandleDto> candles = companyCandles.get(timeFrame);
				assertNotNull(candles, timeFrame + " 타임프레임의 캔들이 존재해야 합니다");
				assertFalse(candles.isEmpty(), timeFrame + " 타임프레임의 캔들이 비어있지 않아야 합니다");

				CandleDto latestCandle = candles.get(candles.size() - 1);
				// 모든 타임프레임의 최신 캔들에 같은 거래가 반영되어야 함
				assertEquals(trade.price().doubleValue(), latestCandle.close(),
						timeFrame + " 타임프레임의 최신 캔들 종가가 거래 가격과 일치해야 합니다");
			}
		}

		@Test
		@DisplayName("TC7.1.3 타임프레임별 캔들 개수 및 범위 확인")
		void shouldMaintainCorrectCandleCountAndRange() throws Exception {
			// Given: 장시간의 거래 데이터 생성 (1시간 분량)
			long baseTime = Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond();
			List<TradeHistoryResponse> trades = new ArrayList<>();
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));

			// 1시간 동안 5분마다 거래 생성 (총 12개 거래)
			for (int i = 0; i < 12; i++) {
				TradeHistoryResponse trade = TradeHistoryResponse.builder()
						.id((long)(i + 1))
						.companyCode(TEST_COMPANY_CODE)
						.price(BigDecimal.valueOf(50000 + i * 100))
						.quantity(BigDecimal.valueOf(10))
						.tradeTime(baseTime + i * 300) // 5분(300초)마다 거래
						.build();
				trades.add(trade);
			}

			// When: 모든 거래 저장
			for (TradeHistoryResponse trade : trades) {
				tradeHistoryService.saveTradeHistory(trade);
			}

			// Then: 타임프레임별 캔들 확인
			Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap =
					getPrivateField(tradeHistoryService, "timeFrameCandleMap");

			Map<TimeFrame, List<CandleDto>> companyCandles = timeFrameCandleMap.get(TEST_COMPANY_CODE);

			// 15초 캔들: 8개 이상 생성되어야 함
			List<CandleDto> second15Candles = companyCandles.get(TimeFrame.SECONDS_15);

			// 1분 캔들: 8개 이상 생성되어야 함
			List<CandleDto> minute1Candles = companyCandles.get(TimeFrame.MINUTE_1);

			// 5분 캔들: 8개 이상 생성되어야 함
			List<CandleDto> minute5Candles = companyCandles.get(TimeFrame.MINUTE_5);

			// 15분 캔들: 2개 이상 생성되어야 함
			List<CandleDto> minute15Candles = companyCandles.get(TimeFrame.MINUTE_15);

			// 30분 캔들: 1개 이상 생성되어야 함
			List<CandleDto> minute30Candles = companyCandles.get(TimeFrame.MINUTE_30);

			// 1시간 캔들: 1개 생성되어야 함
			List<CandleDto> hour1Candles = companyCandles.get(TimeFrame.HOUR_1);

			// 캔들 개수 확인 (시스템 시간의 불확실성을 고려한 완화된 설정)
			assertThat(second15Candles.size()).isGreaterThanOrEqualTo(8); // 최소 8개
			assertThat(minute1Candles.size()).isGreaterThanOrEqualTo(8); // 최소 8개
			assertThat(minute5Candles.size()).isGreaterThanOrEqualTo(8); // 최소 8개
			assertThat(minute15Candles.size()).isGreaterThanOrEqualTo(2); // 최소 2개
			assertThat(minute30Candles.size()).isGreaterThanOrEqualTo(1); // 최소 1개
			assertThat(hour1Candles.size()).isGreaterThanOrEqualTo(1); // 최소 1개

			// 1시간 캔들의 가격 범위 확인
			CandleDto hourCandle = hour1Candles.get(hour1Candles.size() - 1);
			assertThat(hourCandle.high()).isEqualTo(51100.0); // 마지막 거래 가격
			assertThat(hourCandle.low()).isEqualTo(50000.0);  // 첫 거래 가격
			assertThat(hourCandle.volume()).isEqualTo(120);   // 총 거래량 (12거래 * 10수량)
		}

		@Test
		@DisplayName("TC7.1.4 종목 추가에 따라 빈 캔들 채우기")
		void getChartHistory_WithNoData_ShouldReturnDefaultCandle() {
			// Given - 데이터 추가 없음

			// When
			ChartResponseDto chartResponse =
					tradeHistoryService.getChartHistory("NONEXISTENT", TimeFrame.MINUTE_15.getTimeCode());

			// Then
			assertNotNull(chartResponse);
			assertNotNull(chartResponse.candles());
			assertEquals(1, chartResponse.candles().size(), "데이터가 없을 때 기본 캔들이 생성되어야 합니다");
			assertEquals(DEFAULT_PRICE, chartResponse.candles().get(0).open(), "기본 가격이 설정되어야 합니다");
		}

		@Test
		@DisplayName("TC7.1.5 마지막 거래 조회 테스트")
		void getLastTrade_ShouldReturnLastTrade() {
			// Given
			TradeHistoryResponse firstResponse = TradeHistoryResponse.builder()
					.id(1L)
					.companyCode(TEST_COMPANY_CODE)
					.sellOrderId(1L)
					.buyOrderId(2L)
					.quantity(BigDecimal.valueOf(10))
					.price(BigDecimal.valueOf(57500))
					.tradeTime(Instant.now().getEpochSecond()) // 1분 간격
					.build();
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));
			tradeHistoryService.saveTradeHistory(firstResponse);

			TradeHistoryResponse secondResponse = TradeHistoryResponse.builder()
					.id(2L)
					.companyCode(TEST_COMPANY_CODE)
					.sellOrderId(1L)
					.buyOrderId(2L)
					.quantity(BigDecimal.valueOf(20))
					.price(BigDecimal.valueOf(67500))
					.tradeTime(Instant.now().getEpochSecond() + 1) // 1분 간격
					.build();
			tradeHistoryService.saveTradeHistory(secondResponse);

			// When
			Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade(TEST_COMPANY_CODE);

			// Then
			assertTrue(lastTrade.isPresent());
			assertEquals(firstResponse.quantity(), lastTrade.get().getQuantity());
			assertEquals(firstResponse.price(), lastTrade.get().getPrice());
		}

		@Test
		@DisplayName("TC7.2.1 거래 없는 기간의 캔들 생성 확인")
		void shouldHandleEmptyPeriodsInCandleCreation() {
			// Given: 기준 시간 설정
			long currentTime = Instant.now().getEpochSecond();
			long twoHoursAgo = currentTime - 7200; // 2시간 전
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));

			// 2시간 전 거래와 현재 거래 생성 (중간에 거래 없음)
			TradeHistoryResponse oldTrade = TradeHistoryResponse.builder()
					.id(1L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(50000))
					.quantity(BigDecimal.valueOf(10))
					.tradeTime(twoHoursAgo)
					.build();

			TradeHistoryResponse currentTrade = TradeHistoryResponse.builder()
					.id(2L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(52000))
					.quantity(BigDecimal.valueOf(5))
					.tradeTime(currentTime)
					.build();

			// When: 거래 저장 메서드 실행
			tradeHistoryService.saveTradeHistory(oldTrade);
			tradeHistoryService.saveTradeHistory(currentTrade);

			// Then: 캔들 데이터 확인
			Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap =
					getPrivateField(tradeHistoryService, "timeFrameCandleMap");

			// 1시간 캔들 확인
			List<CandleDto> hourCandles = timeFrameCandleMap.get(TEST_COMPANY_CODE).get(TimeFrame.HOUR_1);

			// 최소 3개 이상의 캔들이 있어야 함 (2시간 전 캔들, 중간 캔들, 현재 시간 캔들)
			assertThat(hourCandles.size()).isGreaterThanOrEqualTo(3);

			// 이전 종가 유지
			assertThat(hourCandles.get(0).close()).isEqualTo(hourCandles.get(1).close());
		}

		@Test
		@DisplayName("TC7.2.2 시스템 중단 후 DB에서 캔들 복구")
		void loadTradeHistoryFromDb_ShouldLoadTradesFromDb() {
			// Given
			List<String> companyCodes = List.of(TEST_COMPANY_CODE);
			List<TradeHistory> mockTrades = createMockTradeHistoriesInReverseOrder(TEST_COMPANY_CODE, 5);

			when(tradeHistoryRepository.findDistinctCompanyCodes()).thenReturn(companyCodes);
			// MAX_TRADE_HISTORY 값을 직접 참조
			when(tradeHistoryRepository.findRecentTradesByCompanyCode(eq(TEST_COMPANY_CODE), eq(MAX_TRADE_HISTORY)))
					.thenReturn(mockTrades);

			// When
			tradeHistoryService.loadTradeHistoryFromDb();

			// Then
			verify(tradeHistoryRepository).findDistinctCompanyCodes();
			verify(tradeHistoryRepository).findRecentTradesByCompanyCode(eq(TEST_COMPANY_CODE), anyInt());

			// 메모리에 거래 내역이 로드되었는지 확인 (getLastTrade 메서드를 통해)
			Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade(TEST_COMPANY_CODE);
			assertTrue(lastTrade.isPresent(), "마지막 거래 내역이 메모리에 로드되어야 합니다");
		}

		@Test
		@DisplayName("TC7.2.3 존재하지 않는 종목 코드에 대한 마지막 거래 조회")
		void getLastTrade_WithNonExistentCompany_ShouldReturnEmpty() {
			// When
			Optional<TradeHistory> lastTrade = tradeHistoryService.getLastTrade("NONEXISTENT");

			// Then
			assertFalse(lastTrade.isPresent(), "존재하지 않는 회사 코드에 대해 빈 Optional이 반환되어야 합니다");
		}

		@Test
		@DisplayName("TC7.2.4 캔들 데이터 보관 개수 제한 검증")
		void shouldLimitCandleDataToMaximumKeepNumber() {
			// Given
			// 캔들 데이터 보관 개수보다 많은 거래 내역 생성 (시간 간격을 두고)
			final int TRADES_TO_GENERATE = CANDLE_KEEP_NUMBER + 50; // 보관 개수보다 많은 거래 생성

			long baseTime = Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond();
			List<TradeHistoryResponse> trades = new ArrayList<>();

			// 각 거래가 다른 캔들에 속하도록 시간 조정 (15초 타임프레임 기준)
			for (int i = 0; i < TRADES_TO_GENERATE; i++) {
				TradeHistoryResponse trade = TradeHistoryResponse.builder()
						.id((long)(i + 1))
						.companyCode(TEST_COMPANY_CODE)
						.price(BigDecimal.valueOf(50000 + i * 10))
						.quantity(BigDecimal.valueOf(5))
						.tradeTime(baseTime + i * 15) // 15초마다 거래 (15초 타임프레임 기준으로 각각 다른 캔들)
						.build();
				trades.add(trade);
			}
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));

			// When
			// 모든 거래 내역 저장
			for (TradeHistoryResponse trade : trades) {
				tradeHistoryService.saveTradeHistory(trade);
			}

			// Then
			// private 필드 접근을 통해 캔들 맵 확인
			Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap =
					getPrivateField(tradeHistoryService, "timeFrameCandleMap");

			// 각 타임프레임별 캔들 개수 확인
			Map<TimeFrame, List<CandleDto>> companyCandles = timeFrameCandleMap.get(TEST_COMPANY_CODE);
			assertNotNull(companyCandles, "종목별 캔들 맵이 존재해야 합니다");

			// 모든 타임프레임에 대해 캔들 개수 제한 확인
			for (TimeFrame timeFrame : TimeFrame.values()) {
				List<CandleDto> candles = companyCandles.get(timeFrame);
				assertNotNull(candles, timeFrame + " 타임프레임의 캔들이 존재해야 합니다");

				// 캔들 개수가 CANDLE_KEEP_NUMBER 이하인지 확인
				assertTrue(candles.size() <= CANDLE_KEEP_NUMBER,
						timeFrame + " 타임프레임의 캔들 개수(" + candles.size() + ")가 최대 보관 개수(" +
								CANDLE_KEEP_NUMBER + ")를 초과하지 않아야 합니다");

				// 15초 타임프레임은 정확히 CANDLE_KEEP_NUMBER개여야 함 (각 거래가 다른 캔들에 속하므로)
				if (timeFrame == TimeFrame.SECONDS_15) {
					assertEquals(CANDLE_KEEP_NUMBER, candles.size(),
							"15초 타임프레임의 캔들 개수는 정확히 최대 보관 개수(" + CANDLE_KEEP_NUMBER + ")여야 합니다");

					// 맨 처음 캔들(가장 오래된 캔들)이 마지막에 생성된 캔들에서 CANDLE_KEEP_NUMBER-1 만큼 이전의 거래에 해당하는지 확인
					double expectedOldestPrice = 50000 + (TRADES_TO_GENERATE - CANDLE_KEEP_NUMBER) * 10;
					assertEquals(expectedOldestPrice, candles.get(0).close(),
							"가장 오래된 캔들의 종가는 예상 가격(" + expectedOldestPrice + ")과 일치해야 합니다");

					// 마지막 캔들(가장 최근 캔들)이 마지막 거래 가격과 일치하는지 확인
					double expectedLatestPrice = 50000 + (TRADES_TO_GENERATE - 1) * 10;
					assertEquals(expectedLatestPrice, candles.get(candles.size() - 1).close(),
							"가장 최근 캔들의 종가는 마지막 거래 가격(" + expectedLatestPrice + ")과 일치해야 합니다");
				}
			}
		}

		@Test
		@DisplayName("TC7.2.5 잘못된 시간대 요청 처리")
		void getChartHistory_WithInvalidTimeFrame_ShouldUseDefaultTimeFrame() {
			// Given
			// 먼저 테스트 회사에 대한 거래 데이터 생성
			TradeHistoryResponse trade = TradeHistoryResponse.builder()
					.id(1L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(57500))
					.quantity(BigDecimal.valueOf(10))
					.tradeTime(Instant.now().getEpochSecond())
					.build();


			// 거래 내역 저장
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));
			tradeHistoryService.saveTradeHistory(trade);

			// When: 유효하지 않은 시간대 코드로 차트 요청
			ChartResponseDto invalidTimeFrameResponse =
					tradeHistoryService.getChartHistory(TEST_COMPANY_CODE, "INVALID_TIME_CODE");

			// 존재하는 회사 코드이지만 잘못된 시간대 코드로 요청
			ChartResponseDto nullTimeFrameResponse =
					tradeHistoryService.getChartHistory(TEST_COMPANY_CODE, null);

			// 빈 시간대 코드로 요청
			ChartResponseDto emptyTimeFrameResponse =
					tradeHistoryService.getChartHistory(TEST_COMPANY_CODE, "");

			// Then: 기본 시간대(MINUTE_15)로 응답해야 함
			assertNotNull(invalidTimeFrameResponse, "잘못된 시간대 코드로 요청해도 응답이 반환되어야 합니다");
			assertEquals(TimeFrame.MINUTE_15.getTimeCode(), invalidTimeFrameResponse.timeCode(),
					"잘못된 시간대 코드는 기본값(MINUTE_15)으로 대체되어야 합니다");

			assertNotNull(nullTimeFrameResponse, "null 시간대 코드로 요청해도 응답이 반환되어야 합니다");
			assertEquals(TimeFrame.MINUTE_15.getTimeCode(), nullTimeFrameResponse.timeCode(),
					"null 시간대 코드는 기본값(MINUTE_15)으로 대체되어야 합니다");

			assertNotNull(emptyTimeFrameResponse, "빈 시간대 코드로 요청해도 응답이 반환되어야 합니다");
			assertEquals(TimeFrame.MINUTE_15.getTimeCode(), emptyTimeFrameResponse.timeCode(),
					"빈 시간대 코드는 기본값(MINUTE_15)으로 대체되어야 합니다");

			// 캔들 데이터가 포함되어 있는지 확인
			assertNotNull(invalidTimeFrameResponse.candles(), "응답에 캔들 데이터가 포함되어야 합니다");
			assertFalse(invalidTimeFrameResponse.candles().isEmpty(), "캔들 데이터가 비어 있지 않아야 합니다");

			// 거래 데이터가 올바르게 반영되었는지 확인
			double expectedPrice = trade.price().doubleValue();
			CandleDto latestCandle = invalidTimeFrameResponse.candles()
					.get(invalidTimeFrameResponse.candles().size() - 1);
			assertEquals(expectedPrice, latestCandle.close(),
					"캔들의 종가는 저장된 거래 가격과 일치해야 합니다");
		}

		@Test
		@DisplayName("TC7.2.6 DB에서 캔들 복구시 캔들 정렬 확인")
		void getChartHistory_shouldReturnCandlesSortedByTime() {
			// Given: 현재 시간 및 시간 간격 계산
			long currentTime = Instant.now().getEpochSecond();

			// 시간 순서가 섞인 거래 데이터 생성
			TradeHistoryResponse trade1 = TradeHistoryResponse.builder()
					.id(1L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(50000))
					.quantity(BigDecimal.valueOf(5))
					.tradeTime(currentTime - 3600) // 1시간 전
					.build();

			TradeHistoryResponse trade2 = TradeHistoryResponse.builder()
					.id(2L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(51000))
					.quantity(BigDecimal.valueOf(10))
					.tradeTime(currentTime) // 현재
					.build();

			TradeHistoryResponse trade3 = TradeHistoryResponse.builder()
					.id(3L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(52000))
					.quantity(BigDecimal.valueOf(7))
					.tradeTime(currentTime - 1800) // 30분 전
					.build();

			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));
			// 순서대로 저장 (이 순서는 거래 저장 순서이며, 시간은 섞여 있음)
			tradeHistoryService.saveTradeHistory(trade1);
			tradeHistoryService.saveTradeHistory(trade2);
			tradeHistoryService.saveTradeHistory(trade3);

			// When: getChartHistory 메서드 호출
			ChartResponseDto response = tradeHistoryService.getChartHistory(TEST_COMPANY_CODE,
					TimeFrame.MINUTE_15.getTimeCode());

			// Then: 반환된 캔들이 시간 순으로 정렬되었는지 확인
			assertNotNull(response, "응답이 null이 아니어야 합니다");
			assertNotNull(response.candles(), "캔들 목록이 null이 아니어야 합니다");
			assertTrue(response.candles().size() > 1, "반환된 캔들이 1개 이상 있어야 합니다");

			// 시간 순서로 정렬되었는지 확인
			boolean isSorted = true;
			for (int i = 1; i < response.candles().size(); i++) {
				if (response.candles().get(i - 1).time() > response.candles().get(i).time()) {
					isSorted = false;
					break;
				}
			}

			assertTrue(isSorted, "캔들이 시간 순으로 정렬되어 있어야 합니다");

			// 거래 시간이 포함된 캔들이 반환되었는지 확인
			boolean containsTrade1Time = false;
			boolean containsTrade2Time = false;
			boolean containsTrade3Time = false;

			// 15분 타임프레임 기준으로 캔들 시간 계산
			long timeFrameSeconds = TimeFrame.MINUTE_15.getSeconds();
			long trade1CandleTime = trade1.tradeTime() - (trade1.tradeTime() % timeFrameSeconds);
			long trade2CandleTime = trade2.tradeTime() - (trade2.tradeTime() % timeFrameSeconds);
			long trade3CandleTime = trade3.tradeTime() - (trade3.tradeTime() % timeFrameSeconds);

			for (CandleDto candle : response.candles()) {
				if (candle.time() == trade1CandleTime)
					containsTrade1Time = true;
				if (candle.time() == trade2CandleTime)
					containsTrade2Time = true;
				if (candle.time() == trade3CandleTime)
					containsTrade3Time = true;
			}

			assertTrue(containsTrade1Time, "1시간 전 거래가 포함된 캔들이 있어야 합니다");
			assertTrue(containsTrade2Time, "현재 거래가 포함된 캔들이 있어야 합니다");
			assertTrue(containsTrade3Time, "30분 전 거래가 포함된 캔들이 있어야 합니다");
		}

		@Test
		@DisplayName("TC7.2.7 DB에서 캔들 복구시 유효하지 않은 캔들 필터링 확인")
		void getChartHistory_shouldFilterOutInvalidCandles() {
			// Given
			// 유효한 거래 생성 및 저장
			TradeHistoryResponse validTrade = createMockTradeHistoryResponse(TEST_COMPANY_CODE);
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));
			tradeHistoryService.saveTradeHistory(validTrade);

			// 맵에 접근하여 유효하지 않은 캔들 추가
			Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap =
					getPrivateField(tradeHistoryService, "timeFrameCandleMap");

			Map<TimeFrame, List<CandleDto>> companyTimeFrameMap = timeFrameCandleMap.get(TEST_COMPANY_CODE);
			// 기존 리스트 복사
			List<CandleDto> originalCandles = new ArrayList<>(companyTimeFrameMap.get(TimeFrame.MINUTE_15));

			// 캔들 목록 생성 및 원본에 null 시간 캔들 추가
			List<CandleDto> testCandles = new ArrayList<>(originalCandles);

			// 유효하지 않은 캔들 추가
			testCandles.add(CandleDto.builder()
					.time(null)  // null 시간값
					.open(100.0)
					.high(110.0)
					.low(95.0)
					.close(105.0)
					.volume(1000)
					.build());

			testCandles.add(CandleDto.builder()
					.time(-1L)   // 음수 시간값
					.open(100.0)
					.high(110.0)
					.low(95.0)
					.close(105.0)
					.volume(1000)
					.build());

			// 새 리스트를 맵에 설정
			companyTimeFrameMap.put(TimeFrame.MINUTE_15, testCandles);

			// 유효한 캔들 수 계산
			long validCount = testCandles.stream()
					.filter(c -> c != null && c.time() != null && c.time() > 0)
					.count();

			// When: getChartHistory 메서드 호출
			ChartResponseDto response = tradeHistoryService.getChartHistory(TEST_COMPANY_CODE,
					TimeFrame.MINUTE_15.getTimeCode());

			// Then: 유효한 캔들만 반환되었는지 확인
			assertNotNull(response, "응답이 null이 아니어야 합니다");
			assertNotNull(response.candles(), "캔들 목록이 null이 아니어야 합니다");
			assertEquals(validCount, response.candles().size(),
					"유효한 캔들만 반환되어야 합니다");

			// 모든 반환된 캔들이 유효한지 확인
			for (CandleDto candle : response.candles()) {
				assertNotNull(candle.time(), "모든 캔들 시간은 null이 아니어야 합니다");
				assertTrue(candle.time() > 0, "모든 캔들 시간은 양수여야 합니다");
			}
		}

		@Test
		@DisplayName("TC7.3.1 동시에 들어온 거래의 처리")
		void shouldHandleConcurrentTrades() {
			// Given
			// 테스트할 타임프레임
			TimeFrame testTimeFrame = TimeFrame.MINUTE_1;  // 1분 타임프레임

			// 현재 시간을 기준으로 캔들 경계 계산
			long currentTime = Instant.now().getEpochSecond();
			long timeFrameSeconds = testTimeFrame.getSeconds();
			long currentCandleStartTime = currentTime - (currentTime % timeFrameSeconds);

			// 동일한 시간, 1분 캔들 내에서 서로 다른 가격의 거래 세 개 생성
			TradeHistoryResponse trade1 = TradeHistoryResponse.builder()
					.id(1L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(50000))
					.quantity(BigDecimal.valueOf(5))
					.tradeTime(currentCandleStartTime)
					.build();

			TradeHistoryResponse trade2 = TradeHistoryResponse.builder()
					.id(2L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(51000))
					.quantity(BigDecimal.valueOf(10))
					.tradeTime(currentCandleStartTime)
					.build();

			TradeHistoryResponse trade3 = TradeHistoryResponse.builder()
					.id(3L)
					.companyCode(TEST_COMPANY_CODE)
					.price(BigDecimal.valueOf(49000))
					.quantity(BigDecimal.valueOf(7))
					.tradeTime(currentCandleStartTime)
					.build();
			when(orderRepository.findById(any())).thenReturn(Optional.of(sellOrder));
			// When
			// 거래 저장 - 세 거래 모두 동일한 캔들에 반영되어야 함
			tradeHistoryService.saveTradeHistory(trade1);
			tradeHistoryService.saveTradeHistory(trade2);
			tradeHistoryService.saveTradeHistory(trade3);

			// Then
			// 캔들 맵 가져오기
			Map<String, Map<TimeFrame, List<CandleDto>>> timeFrameCandleMap =
					getPrivateField(tradeHistoryService, "timeFrameCandleMap");

			Map<TimeFrame, List<CandleDto>> companyCandles = timeFrameCandleMap.get(TEST_COMPANY_CODE);
			assertNotNull(companyCandles, "종목 캔들 맵이 존재해야 합니다");

			List<CandleDto> candlesForTimeFrame = companyCandles.get(testTimeFrame);
			assertNotNull(candlesForTimeFrame, testTimeFrame + " 타임프레임의 캔들이 존재해야 합니다");
			assertFalse(candlesForTimeFrame.isEmpty(), "캔들 목록이 비어있지 않아야 합니다");

			// 현재 시간의 캔들 찾기
			CandleDto currentCandle = null;
			for (CandleDto candle : candlesForTimeFrame) {
				if (candle.time() == currentCandleStartTime) {
					currentCandle = candle;
					break;
				}
			}

			// 캔들 검증
			assertNotNull(currentCandle, "현재 시간의 캔들이 존재해야 합니다");

			// 세 거래가 모두 올바르게 반영되었는지 확인
			assertEquals(50000, currentCandle.open(), "캔들의 시가는 첫 번째 거래의 가격이어야 합니다");
			assertEquals(49000, currentCandle.close(), "캔들의 종가는 마지막 거래의 가격이어야 합니다");
			assertEquals(51000, currentCandle.high(), "캔들의 고가는 세 거래 중 가장 높은 가격이어야 합니다");
			assertEquals(49000, currentCandle.low(), "캔들의 저가는 세 거래 중 가장 낮은 가격이어야 합니다");
			assertEquals(5 + 10 + 7, currentCandle.volume(), "캔들의 거래량은 세 거래의 합이어야 합니다");
		}

	}

	// 이미 역순으로 정렬된 거래 내역 생성 (최신 거래가 먼저 오도록)
	private List<TradeHistory> createMockTradeHistoriesInReverseOrder(String companyCode, int count) {
		List<TradeHistory> trades = new ArrayList<>(count);
		long currentTime = Instant.now().getEpochSecond();

		// 최신 거래부터 추가 (i=0이 가장 최신 거래)
		for (int i = 0; i < count; i++) {
			trades.add(TradeHistory.builder()
					.id((long)i)
					.companyCode(companyCode)
					.sellOrderId((long)(i + 100))
					.buyOrderId((long)(i + 200))
					.quantity(BigDecimal.valueOf(10))
					.price(BigDecimal.valueOf(57500 + i * 100))
					.tradeTime(currentTime - i * 60) // 1분 간격 (최신 거래가 먼저)
					.build());
		}
		return trades;
	}

	private TradeHistoryResponse createMockTradeHistoryResponse(String companyCode) {
		return TradeHistoryResponse.builder()
				.id(1L)
				.companyCode(companyCode)
				.sellOrderId(101L)
				.buyOrderId(201L)
				.quantity(BigDecimal.valueOf(10))
				.price(BigDecimal.valueOf(57500))
				.tradeTime(Instant.now().getEpochSecond())
				.build();
	}

	private Order createOrder(Long id, Type type) {
		return Order.builder()
				.id(id)
				.companyCode("005930")
				.type(type)
				.totalQuantity(new BigDecimal("10"))
				.remainingQuantity(new BigDecimal("10"))
				.status(OrderStatus.ACTIVE)
				.price(new BigDecimal("1000"))
				.timestamp(LocalDateTime.now())
				.status(OrderStatus.ACTIVE)
				.build();
	}

	// 리플렉션을 사용하여 private 필드 값 가져오기
	@SuppressWarnings("unchecked")
	private <T> T getPrivateField(final Object object, final String fieldName) {
		try {
			Field field = object.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T)field.get(object);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get private field: " + fieldName, e);
		}
	}

}
