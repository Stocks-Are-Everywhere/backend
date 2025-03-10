package org.scoula.backend.order.dto.ranking;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TurnoverRateRankingDto extends BaseRankingDto {
	private Double turnoverRate;
}
