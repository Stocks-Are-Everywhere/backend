package org.scoula.backend.order.dto.ranking;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ListedSharesRankingDto extends BaseRankingDto {
	private Integer listedShares;
}
