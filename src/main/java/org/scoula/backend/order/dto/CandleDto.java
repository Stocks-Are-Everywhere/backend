package org.scoula.backend.order.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CandleDto {
	private long time;       // Unix timestamp (초 단위)
	private double open;     // 시가
	private double high;     // 고가
	private double low;      // 저가
	private double close;    // 종가
	private int volume;      // 거래량

	public CandleDto() {
	}

	public CandleDto(long time, double open, double high, double low, double close, int volume) {
		this.time = time;
		this.open = open;
		this.high = high;
		this.low = low;
		this.close = close;
		this.volume = volume;
	}
}
