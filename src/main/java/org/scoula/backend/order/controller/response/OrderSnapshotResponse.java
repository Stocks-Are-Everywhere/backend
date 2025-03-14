package org.scoula.backend.order.controller.response;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.scoula.backend.order.domain.Order;

public record OrderSnapshotResponse(
		String companyCode,
		ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<Order>> sellOrders,
		ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<Order>> buyOrders
) {
}
