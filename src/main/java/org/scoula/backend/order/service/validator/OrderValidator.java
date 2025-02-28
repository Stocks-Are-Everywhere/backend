package org.scoula.backend.order.service.validator;

import java.math.BigDecimal;
import java.util.Arrays;

import org.scoula.backend.order.service.exception.OrderPriceQuotationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public enum OrderValidator {

	UNIT_1(                                // ~ 2,000원 미만
			new BigDecimal("0"),
			new BigDecimal("2000"),
			new BigDecimal("1")
	),
	UNIT_5(                            // 2,000원 ~ 5,000원
			new BigDecimal("2000"),
			new BigDecimal("5000"),
			new BigDecimal("5")
	),
	UNIT_10(                            // 5,000원 ~ 20,000원
			new BigDecimal("5000"),
			new BigDecimal("20000"),
			new BigDecimal("10")
	),
	UNIT_50(                            // 20,000원 ~ 50,000원
			new BigDecimal("20000"),
			new BigDecimal("50000"),
			new BigDecimal("50")
	),
	UNIT_100(                            // 50,000원 ~ 200,000원
			new BigDecimal("50000"),
			new BigDecimal("200000"),
			new BigDecimal("100")
	),
	UNIT_500(                            // 200,000원 ~ 500,000원
			new BigDecimal("200000"),
			new BigDecimal("500000"),
			new BigDecimal("500")
	),
	UNIT_1000(                            // 500,000원 이상
			new BigDecimal("500000"),
			new BigDecimal(Integer.MAX_VALUE),
			new BigDecimal("1000")
	);

	private final BigDecimal minPrice;
	private final BigDecimal maxPrice;
	private final BigDecimal unit;

	// 지정가 주문 가격 범위 유효성 검증
	public static OrderValidator getUnitByPrice(final BigDecimal price) {
		return Arrays.stream(values())
				.filter(unit ->
						price.compareTo(unit.minPrice) >= 0 &&
								price.compareTo(unit.maxPrice) < 0
				)
				.findFirst()
				.orElseThrow(() ->
						new OrderPriceQuotationException("주문 가격이 유효하지 않습니다.")
				);
	}

	// 지정가 주문 가격 견젹 유효성 검증
	public void isValidPrice(final BigDecimal price) {
		if (!(price.remainder(unit)
				.compareTo(BigDecimal.ZERO) == 0)) {
			throw new OrderPriceQuotationException("주문 가격이 호가 단위에 맞지 않습니다.");
		}

		log.debug("주문 가격 견적 유효성 검증. Price: {}, Unit: {}",
				price.toPlainString(),
				this.unit.toPlainString());
	}

}
