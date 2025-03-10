package org.scoula.backend.order.domain;

public enum TimeFrame {
	SECONDS_15(15L, "15s"), // 15초
	MINUTE_1(60L, "1m"), // 1분
	MINUTE_5(300L, "5m"), // 5분
	MINUTE_15(900L, "15m"), // 15분
	MINUTE_30(1800L, "30m"), // 30분
	HOUR_1(3600L, "1h"); // 1시간

	private final Long seconds;
	private final String timeCode;

	TimeFrame(final Long seconds, final String timeCode) {
		this.seconds = seconds;
		this.timeCode = timeCode;
	}

	public Long getSeconds() {
		return seconds;
	}

	public String getTimeCode() {
		return timeCode;
	}
}
