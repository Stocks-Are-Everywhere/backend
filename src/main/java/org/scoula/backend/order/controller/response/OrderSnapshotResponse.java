package org.scoula.backend.order.controller.response;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.TreeMap;

import org.scoula.backend.order.domain.Order;

public record OrderSnapshotResponse(
		String companyCode,
		TreeMap<BigDecimal, Queue<Order>> sellOrders,
		TreeMap<BigDecimal, Queue<Order>> buyOrders
) {
}
