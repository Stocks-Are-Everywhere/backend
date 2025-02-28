package org.scoula.backend.order.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChartUpdateDto {
	private double price;
	private int volume;

	// 기본 생성자
	public ChartUpdateDto() {
	}

	public ChartUpdateDto(double price, int volume) {
		this.price = price;
		this.volume = volume;
	}
}
