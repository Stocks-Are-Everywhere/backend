package org.scoula.backend.order.controller.response;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

import org.scoula.backend.order.domain.Order;

public record OrderSnapshotResponse(
	String companyCode,
	ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders,
	ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders
) {
}
