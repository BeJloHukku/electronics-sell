package ru.mirea.orders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

final class Order {
    private final long orderId;
    private final Request request;
    private final Instant createdAt;
    private final Map<Long, OrderItem> items = new LinkedHashMap<>();
    private String status;
    private Instant updatedAt;

    Order(long orderId, Request request, String status, Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.request = request;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    long id() { return orderId; }
    String status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    Request request() { return request; }
    List<OrderItem> items() { return List.copyOf(items.values()); }

    void addItem(OrderItem item) {
        if (items.putIfAbsent(item.source().id(), item) != null) {
            throw new IllegalArgumentException("order_items.request_item_id: повтор значения.");
        }
    }

    OrderItem itemFor(RequestItem source) {
        return items.get(source.id());
    }

    public void setStatus(String status, Instant at) {
        this.status = status;
        updatedAt = at;
    }

    public OptionalLong calculateTotal(Instant at) {
        Map<OrderItem, Long> prices = new LinkedHashMap<>();
        long total = 0;
        for (RequestItem source : request.items()) {
            Product product = source.product();
            List<Price> active = product == null ? List.of() : product.activePrices(at);
            if (active.size() != 1) {
                return OptionalLong.empty();
            }
            long unitPrice = active.get(0).value();
            prices.put(itemFor(source), unitPrice);
            total = Math.addExact(total, Math.multiplyExact(unitPrice, source.quantity()));
        }
        prices.forEach(OrderItem::fixPrice);
        return OptionalLong.of(total);
    }

    long fixedTotal() {
        long total = 0;
        for (OrderItem item : items.values()) {
            total = Math.addExact(total, item.subtotal());
        }
        return total;
    }
}
