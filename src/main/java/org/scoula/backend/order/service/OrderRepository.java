package org.scoula.backend.order.service;

import org.scoula.backend.order.domain.Order;

import java.util.List;

public interface OrderRepository {

    Order save(final Order order);

    Order getById(final Long id);

    List<Order> findByCompanyCode(final String number);
}
