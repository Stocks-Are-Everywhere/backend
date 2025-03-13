package org.scoula.backend.fake;

import org.scoula.backend.order.domain.Order;
import org.scoula.backend.order.service.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TestOrderRepository implements OrderRepository {

    private final List<Order> elements = new ArrayList<>();

    @Override
    public Order save(Order order) {
        if (hasElement(order)) {
            elements.remove(elements.indexOf(order));
            elements.add(order);
            return order;
        }
        Order saved = Order.builder()
                .id(order.getId())
                .companyCode(order.getCompanyCode())
                .type(order.getType())
                .totalQuantity(order.getTotalQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .status(order.getStatus())
                .price(order.getPrice())
                .account(order.getAccount())
                .timestamp(order.getTimestamp())
                .build();
        elements.add(order);
        return saved;
    }

    private boolean hasElement(Order order) {
        if (order.getId() == null) {
            return false;
        }
        return elements.stream()
                .anyMatch(o -> o.getId().equals(order.getId()));
    }

    @Override
    public Order getById(Long id) {
        return elements.stream()
                .filter(e -> Objects.equals(e.getId(), id))
                .findAny()
                .orElseThrow();
    }

    @Override
    public List<Order> findByCompanyCode(String number) {
        return elements.stream().
                filter(order -> order.getCompanyCode().equals(number))
                .toList();
    }
}
