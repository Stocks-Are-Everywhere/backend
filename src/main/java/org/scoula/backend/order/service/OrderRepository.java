package org.scoula.backend.order.service;

import org.scoula.backend.order.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(final Order order);

    Order getById(final Long id);

    List<Order> findByCompanyCode(final String number);
}
