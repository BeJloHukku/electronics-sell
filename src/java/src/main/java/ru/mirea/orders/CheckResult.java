package java.src.main.java.ru.mirea.orders;

import java.time.Instant;

final class CheckResult {
    private final long checkResultId;
    private final CheckType type;
    private final Request request;
    private final OrderItem orderItem;
    private final boolean passed;
    private final String message;
    private final Instant checkedAt;

    CheckResult(long checkResultId, CheckType type, Request request, OrderItem orderItem,
                boolean passed, String message, Instant checkedAt) {
        if ((request == null) == (orderItem == null)) {
            throw new IllegalArgumentException("Результату проверки нужна ровно одна ссылка.");
        }
        this.checkResultId = checkResultId;
        this.type = type;
        this.request = request;
        this.orderItem = orderItem;
        this.passed = passed;
        this.message = message;
        this.checkedAt = checkedAt;
    }

    long id() { return checkResultId; }
    CheckType type() { return type; }
    Request request() { return request; }
    OrderItem orderItem() { return orderItem; }
    Instant checkedAt() { return checkedAt; }

    public boolean isPassed() { return passed; }
    public String message() { return message; }

    boolean belongsTo(Request value) {
        return request == value || (orderItem != null && orderItem.order().request() == value);
    }

    CheckResult withId(long id) {
        return new CheckResult(id, type, request, orderItem, passed, message, checkedAt);
    }
}
