package org.scoula.backend.order.controller.response;

import java.util.SortedMap;
import java.util.TreeMap;

import org.scoula.backend.order.service.orderbook.OrderStorage;
import org.scoula.backend.order.service.orderbook.Price;

public record OrderSnapshotResponse(
		String companyCode,
		SortedMap<Price, OrderStorage> sellOrders,
		SortedMap<Price, OrderStorage> buyOrders
) {
}
