package ru.mirea.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class OrderProcessingService {
    static final String RESERVED = "Весь состав заказа зарезервирован.";

    private final ShopData data;
    private final CheckRule requestDataCheck;
    private final CheckRule stockAvailabilityCheck;

    OrderProcessingService(ShopData data, CheckRule requestDataCheck, CheckRule stockAvailabilityCheck) {
        this.data = data;
        this.requestDataCheck = requestDataCheck;
        this.stockAvailabilityCheck = stockAvailabilityCheck;
    }

    public void processRequest(Request request, Instant at) {
        if (request.isFinal()) {
            return;
        }
        List<CheckResult> completeness = requestDataCheck.check(request, at);
        data.recordChecks(completeness);
        if (!allPassed(completeness)) {
            finish(request, "needs_clarification", at);
            return;
        }

        Order order = request.order() == null ? data.createOrder(request, at) : request.order();
        order.setStatus("processing", at);
        List<CheckResult> stock = stockAvailabilityCheck.check(request, at);
        data.recordChecks(stock);
        if (!allPassed(stock)) {
            finish(request, "rejected", at);
            return;
        }

        if (order.calculateTotal(at).isEmpty()) {
            recordReservation(order, false, "Резервирование не выполнено: для одной или нескольких позиций "
                    + "нет единственной действующей цены.", at);
            finish(request, "awaiting_price", at);
            return;
        }

        boolean reserved = reserveOrder(order, at);
        recordReservation(order, reserved,
                reserved ? RESERVED : "Не удалось зарезервировать весь состав заказа.", at);
        finish(request, reserved ? "confirmed" : "rejected", at);
    }

    public boolean reserveOrder(Order order, Instant at) {
        List<OrderItem> items = order.items();
        boolean alreadyReserved = items.stream().anyMatch(item -> item.reservation() != null);
        if (alreadyReserved) {
            return order.status().equals("confirmed") && items.stream().allMatch(item ->
                    item.reservation() != null && item.source().hasValidQuantity()
                            && item.reservation().covers(item.source().quantity()));
        }
        if (!order.status().equals("processing") || items.isEmpty()
                || items.stream().anyMatch(item -> item.price() == null)) {
            return false;
        }
        if (!allPassed(stockAvailabilityCheck.check(order.request(), at))) {
            return false;
        }
        data.reserveItems(items, at);
        return true;
    }

    private void recordReservation(Order order, boolean passed, String message, Instant at) {
        CheckType type = data.checkType("reservation");
        List<CheckResult> checks = new ArrayList<>();
        for (OrderItem item : order.items()) {
            checks.add(new CheckResult(0, type, null, item, passed, message, at));
        }
        data.recordChecks(checks);
    }

    private static boolean allPassed(List<CheckResult> checks) {
        return !checks.isEmpty() && checks.stream().allMatch(CheckResult::isPassed);
    }

    private static void finish(Request request, String status, Instant at) {
        if (request.order() != null) {
            request.order().setStatus(status, at);
        }
        request.changeStatus(status);
    }
}
