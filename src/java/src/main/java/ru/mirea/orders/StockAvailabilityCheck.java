package java.src.main.java.ru.mirea.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class StockAvailabilityCheck implements CheckRule {
    private final CheckType type;

    StockAvailabilityCheck(CheckType type) {
        this.type = type;
    }

    @Override
    public List<CheckResult> check(Request request, Instant at) {
        List<RequestItem> items = request.items();
        Map<Long, Long> required = new HashMap<>();
        for (RequestItem item : items) {
            if (item.productId() == null || !item.hasValidQuantity()) {
                throw new IllegalArgumentException("До проверки остатков необходимо проверить состав заявки.");
            }
            required.merge(item.productId(), item.quantity().longValue(), Math::addExact);
        }
        Order order = request.order();
        List<CheckResult> results = new ArrayList<>(items.size());
        for (RequestItem item : items) {
            Product product = item.product();
            long id = item.productId();
            String failure = stockProblem(product, id, required.get(id));
            String message = failure == null ? "Товар " + id + " доступен в нужном количестве." : failure;
            results.add(new CheckResult(0, type, null, order.itemFor(item), failure == null, message, at));
        }
        return results;
    }

    private String stockProblem(Product product, long id, long quantity) {
        if (product == null) {
            return "Товар " + id + " отсутствует в каталоге.";
        }
        Stock stock = product.stock();
        if (stock == null) {
            return "Нет данных об остатке товара " + id + ".";
        }
        if (quantity > stock.availableQuantity()) {
            return "Товар " + id + ": требуется " + quantity
                    + ", доступно " + stock.availableQuantity() + ".";
        }
        return null;
    }
}
