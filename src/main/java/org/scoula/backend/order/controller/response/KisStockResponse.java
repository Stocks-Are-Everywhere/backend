package org.scoula.backend.order.controller.response;

import lombok.Data;

@Data
public class KisStockResponse {
	private Long time;          // 체결 시각
	private double currentPrice;  // 현재가
	private double openPrice;     // 시가
	private double highPrice;     // 고가
	private double lowPrice;      // 저가
	private double volume;        // 체결량
	private double accVolume;     // 누적거래량
	private String changeSign;    // 전일대비구분
	private double changePrice;   // 전일대비
	private double changeRate;    // 등락률
	private double askPrice;      // 매도호가
	private double bidPrice;      // 매수호가
	private long askVolume;       // 매도잔량
	private long bidVolume;       // 매수잔량
}
