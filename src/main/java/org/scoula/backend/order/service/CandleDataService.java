package org.scoula.backend.order.service;

// @Slf4j
// @RequiredArgsConstructor
// @Service
// public class CandleDataService {
//
// 	private final CandleDataRepositoryImpl candleDataRepository;
// 	private final TradeHistoryService tradeHistoryService;
//
// 	// 자주 사용되는 타임 프레임 정의
// 	// Todo : 자주 사용되는 타임 프레임만 DB에 저장하고
// 	// 나머지 캔들은 TradeHistory를 통해 데이터를 가져오도록 개선 가능합니다.
// 	private static final TimeFrame[] FREQUENT_TIMEFRAMES = {
// 			TimeFrame.SECONDS_15, TimeFrame.MINUTE_1, TimeFrame.MINUTE_5,
// 			TimeFrame.MINUTE_15, TimeFrame.MINUTE_30, TimeFrame.HOUR_1
// 	};
//
// 	/**
// 	 * 주기적으로 자주 사용되는 타임프레임의 캔들 데이터 저장
// 	 * 1분마다 실행
// 	 */
// 	public void persistFrequentTimeFrames() {
// 		log.info("자주 사용되는 캔들 데이터 DB에 저장");
//
// 		// 모든 종목 코드에 대해 처리
// 		for (String companyCode : tradeHistoryService.getRecentTradesMap().keySet()) {
// 			persistCompanyCandles(companyCode);
// 		}
// 	}
//
// 	/**
// 	 * 특정 종목의 캔들 데이터 저장
// 	 */
// 	private void persistCompanyCandles(final String companyCode) {
// 		for (TimeFrame timeFrame : FREQUENT_TIMEFRAMES) {
// 			// 현재 메모리에 있는 캔들 데이터 가져오기
// 			final List<CandleDto> candles = tradeHistoryService.getTimeFrameCandleMap()
// 					.getOrDefault(companyCode, new java.util.HashMap<>())
// 					.getOrDefault(timeFrame, new ArrayList<>());
//
// 			if (!candles.isEmpty()) {
// 				// 최신 캔들만 저장
// 				saveLatestCandles(companyCode, timeFrame, candles);
// 			}
// 		}
// 	}
//
// 	/**
// 	 * 최신 캔들 저장 (변경된 캔들만)
// 	 */
// 	private void saveLatestCandles(final String companyCode, final TimeFrame timeFrame, final List<CandleDto> candles) {
// 		// 일반적으로 마지막 캔들만 업데이트하면 되지만,
// 		// 서비스 중단 후 재시작 시 여러 캔들이 누락될 수 있으므로
// 		// 마지막 N개 캔들에 대해 저장 처리
//
// 		final Integer candlesToSave = Math.min(candles.size(), 5); // 최대 5개까지 저장
// 		final List<CandleDto> recentCandles = candles.subList(candles.size() - candlesToSave, candles.size());
//
// 		for (CandleDto candleDto : recentCandles) {
// 			// DB에 해당 캔들이 있는지 확인
// 			final CandleData existingCandle = candleDataRepository.findByCompanyCodeAndTimeFrameCodeAndTime(
// 					companyCode, timeFrame.getTimeCode(), candleDto.time());
//
// 			if (existingCandle == null) {
// 				// 새 캔들 추가
// 				final CandleData newCandle = convertDtoToEntity(companyCode, timeFrame.getTimeCode(), candleDto);
// 				candleDataRepository.save(newCandle);
// 			} else {
// 				// 캔들 데이터가 변경되었다면 업데이트
// 				if (isUpdated(existingCandle, candleDto)) {
// 					final CandleData updatedCandle = CandleData.builder()
// 							.id(existingCandle.getId())
// 							.companyCode(companyCode)
// 							.timeFrameCode(timeFrame.getTimeCode())
// 							.time(candleDto.time())
// 							.open(candleDto.open())
// 							.high(candleDto.high())
// 							.low(candleDto.low())
// 							.close(candleDto.close())
// 							.volume(candleDto.volume())
// 							.build();
//
// 					candleDataRepository.save(updatedCandle);
// 				}
// 			}
// 		}
// 	}
//
// 	/**
// 	 * 캔들 데이터 엔티티와 Dto 비교 (변경 여부 확인)
// 	 */
// 	private boolean isUpdated(final CandleData entity, final CandleDto dto) {
// 		return !entity.getOpen().equals(dto.open()) ||
// 				!entity.getHigh().equals(dto.high()) ||
// 				!entity.getLow().equals(dto.low()) ||
// 				!entity.getClose().equals(dto.close()) ||
// 				!entity.getVolume().equals(dto.volume());
// 	}
//
// 	/**
// 	 * Dto를 엔티티로 변환
// 	 */
// 	private CandleData convertDtoToEntity(final String companyCode, final String timeFrameCode, final CandleDto dto) {
// 		return CandleData.builder()
// 				.companyCode(companyCode)
// 				.timeFrameCode(timeFrameCode)
// 				.time(dto.time())
// 				.open(dto.open())
// 				.high(dto.high())
// 				.low(dto.low())
// 				.close(dto.close())
// 				.volume(dto.volume())
// 				.build();
// 	}
//
// 	/**
// 	 * 엔티티를 Dto로 변환
// 	 */
// 	private CandleDto convertEntityToDto(final CandleData entity) {
// 		return CandleDto.builder()
// 				.time(entity.getTime())
// 				.open(entity.getOpen())
// 				.high(entity.getHigh())
// 				.low(entity.getLow())
// 				.close(entity.getClose())
// 				.volume(entity.getVolume())
// 				.build();
// 	}
//
// 	/**
// 	 * 서버 시작 시 DB에서 캔들 데이터 로드
// 	 * @PostConstruct로 서버 시작시 자동 실행
// 	 */
// 	@PostConstruct
// 	@Transactional(readOnly = true)
// 	public void loadCandlesFromDb() {
// 		log.info("DB에서 캔들 데이터 읽기");
//
// 		for (String companyCode : tradeHistoryService.getRecentTradesMap().keySet()) {
// 			for (TimeFrame timeFrame : FREQUENT_TIMEFRAMES) {
// 				loadCompanyTimeFrameCandles(companyCode, timeFrame);
// 			}
// 		}
// 	}
//
// 	/**
// 	 * 특정 종목, 특정 타임프레임의 캔들 데이터 로드
// 	 */
// 	private void loadCompanyTimeFrameCandles(final String companyCode, final TimeFrame timeFrame) {
// 		// 최신 캔들 30개 로드 (CANDLE_KEEP_NUMBER와 동일하게 설정)
// 		final List<CandleData> candles = candleDataRepository.findLatestCandles(
// 				companyCode, timeFrame.getTimeCode(), PageRequest.of(0, 30));
//
// 		if (!candles.isEmpty()) {
// 			// 최신순으로 가져왔으므로 다시 시간 순으로 정렬
// 			List<CandleDto> candleDtos = candles.stream()
// 					.sorted((c1, c2) -> c1.getTime().compareTo(c2.getTime()))
// 					.map(this::convertEntityToDto)
// 					.toList();
//
// 			// TradeHistoryService의 메모리 맵에 로드
// 			tradeHistoryService.setTimeFrameCandles(companyCode, timeFrame, candleDtos);
// 		}
//
// 	}
// }
