package org.scoula.backend.order.repository;

// @Repository
// public interface CandleDataJpaRepository extends JpaRepository<CandleData, Long> {
//
// 	// 특정 종목, 특정 타임프레임의 캔들 데이터 조회 (최신순)
// 	List<CandleData> findByCompanyCodeAndTimeFrameCodeOrderByTimeDesc(
// 			final String companyCode, final String timeFrameCode, final Pageable pageable);
//
// 	// 특정 종목, 특정 타임프레임, 특정 시간의 캔들 데이터 조회
// 	CandleData findByCompanyCodeAndTimeFrameCodeAndTime(
// 			final String companyCode, final String timeFrameCode, final Long time);
//
// 	// 특정 종목, 특정 타임프레임의 가장 최근 캔들 조회
// 	@Query("SELECT c FROM CandleData c WHERE c.companyCode = :companyCode AND" +
// 			"c.timeFrameCode = :timeFrameCode ORDER BY c.time DESC")
// 	List<CandleData> findLatestCandles(final String companyCode, final String timeFrameCode, final Pageable pageable);
// }
