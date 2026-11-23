package java.src.main.java.ru.mirea.orders;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class ShopData {
    private final Map<Long, Request> requests;
    private final Map<Long, Reservation> reservations;
    private final Map<String, CheckType> checkTypes;
    private final List<CheckResult> checks;
    private long lastOrderId;
    private long lastOrderItemId;
    private long lastReservationId;
    private long lastCheckId;

    ShopData(Map<Long, Request> requests, Map<Long, Order> orders, Map<Long, OrderItem> orderItems,
             Map<Long, Reservation> reservations, Map<String, CheckType> checkTypes, List<CheckResult> checks) {
        this.requests = requests;
        this.reservations = reservations;
        this.checkTypes = checkTypes;
        this.checks = checks;
        lastOrderId = maxId(orders);
        lastOrderItemId = maxId(orderItems);
        lastReservationId = maxId(reservations);
        lastCheckId = checks.stream().mapToLong(CheckResult::id).max().orElse(0);
    }

    private static long maxId(Map<Long, ?> rows) {
        return rows.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    Collection<Request> requests() { return List.copyOf(requests.values()); }
    Collection<Reservation> reservations() { return List.copyOf(reservations.values()); }
    List<CheckResult> checks() { return List.copyOf(checks); }

    Request request(long id) {
        Request request = requests.get(id);
        if (request == null) {
            throw new IllegalArgumentException("Заявка " + id + " не найдена.");
        }
        return request;
    }

    CheckType checkType(String code) {
        return checkTypes.get(code);
    }

    Order createOrder(Request request, Instant at) {
        lastOrderId = Math.incrementExact(lastOrderId);
        Order order = new Order(lastOrderId, request, "processing", at, at);
        for (RequestItem source : request.items()) {
            lastOrderItemId = Math.incrementExact(lastOrderItemId);
            OrderItem row = new OrderItem(lastOrderItemId, order, source, null);
            order.addItem(row);
        }
        request.attachOrder(order);
        return order;
    }

    void recordChecks(List<CheckResult> results) {
        for (CheckResult result : results) {
            lastCheckId = Math.incrementExact(lastCheckId);
            checks.add(result.withId(lastCheckId));
        }
    }

    void reserveItems(List<OrderItem> items, Instant at) {
        Math.addExact(lastReservationId, items.size());
        for (OrderItem item : items) {
            Reservation reservation = new Reservation(++lastReservationId, item, item.source().quantity(), at);
            item.source().product().stock().debit(reservation.quantity(), at);
            item.attachReservation(reservation);
            reservations.put(reservation.id(), reservation);
        }
    }
}
