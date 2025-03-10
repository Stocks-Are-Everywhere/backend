package org.scoula.backend.order.dto.ranking;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class BaseRankingDto {
	private String companyCode;
	private String companyName;
	private Integer rank;
}
