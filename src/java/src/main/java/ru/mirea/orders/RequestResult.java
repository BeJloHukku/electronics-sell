package java.src.main.java.ru.mirea.orders;

import java.util.List;

final class RequestResult {
    private final long id;
    private final String status;
    private final Long orderId;
    private final Long total;
    private final String message;

    RequestResult(Request request, List<CheckResult> history) {
        id = request.id();
        status = request.status();
        Order order = request.order();
        orderId = order == null ? null : order.id();
        total = status.equals("confirmed") ? order.fixedTotal() : null;
        message = describe(request, history);
    }

    long id() { return id; }
    String status() { return status; }
    Long orderId() { return orderId; }
    Long total() { return total; }
    String message() { return message; }

    private static String describe(Request request, List<CheckResult> history) {
        switch (request.status()) {
            case "new":
                return "Заявка ожидает обработки.";
            case "confirmed":
                return OrderProcessingService.RESERVED;
            default:
                for (int i = history.size() - 1; i >= 0; i--) {
                    CheckResult check = history.get(i);
                    if (!check.isPassed() && check.belongsTo(request)) {
                        return check.message() == null ? "None" : check.message();
                    }
                }
                if (request.status().equals("needs_clarification")) return "Требуется уточнение данных.";
                if (request.status().equals("awaiting_price")) return "Требуется уточнение прайс-листа.";
                return "Подтверждение отклонено.";
        }
    }
}
