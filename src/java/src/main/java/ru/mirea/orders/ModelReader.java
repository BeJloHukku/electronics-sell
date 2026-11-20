package ru.mirea.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static ru.mirea.orders.JsonTables.*;

final class ModelReader {
    private static final Set<String> ORDER_STATUSES =
            Set.of("needs_clarification", "awaiting_price", "confirmed", "rejected");

    private final JsonTables tables;
    private final Map<Long, Stock> stocks = new LinkedHashMap<>();

    ModelReader(JsonTables tables) {
        this.tables = tables;
    }

    Map<Long, Stock> stocks() {
        return stocks;
    }

    ShopData read() {
        Map<Long, Client> clients = readClients();
        Map<Long, Address> addresses = readTable("addresses", (id, row) -> new Address(id,
                text(row, "country", true), text(row, "city", true),
                text(row, "street", true), text(row, "house", true)));
        Map<Long, Product> products = readProducts();
        Map<Long, Request> requests = readRequests(clients, addresses);
        Map<Long, RequestItem> requestItems = readRequestItems(requests, products);
        Map<Long, Order> orders = readOrders(requests);
        Map<Long, OrderItem> orderItems = readOrderItems(requestItems, orders);
        Map<Long, Reservation> reservations = readReservations(orderItems);
        validateOrders(requests, orders);

        Map<Long, CheckType> types = readTable("check_types", (id, row) -> new CheckType(id,
                text(row, "code", false), text(row, "name", true)));
        Map<String, CheckType> codes = new LinkedHashMap<>();
        for (CheckType type : types.values()) {
            require(codes.putIfAbsent(type.code(), type) == null, "check_types.code: повтор значения.");
        }
        require(codes.keySet().containsAll(Set.of("request_data", "stock", "reservation")),
                "В check_types нужны коды request_data, stock, reservation.");
        List<CheckResult> checks = readChecks(requests, orderItems, types);
        return new ShopData(requests, orders, orderItems, reservations, codes, checks);
    }

    private Map<Long, Client> readClients() {
        Map<Long, Client> clients = readTable("clients", (id, row) -> new Client(id, text(row, "first_name", true),
                text(row, "middle_name", true), text(row, "last_name", true)));
        tables.rows("emails").forEach((id, row) -> reference(clients, row, "client_id")
                .addContact(new Email(id, text(row, "email", true))));
        tables.rows("phone_numbers").forEach((id, row) -> reference(clients, row, "client_id")
                .addContact(new PhoneNumber(id, text(row, "phone_number", true))));
        return clients;
    }

    private Map<Long, Product> readProducts() {
        Map<Long, Product> products = readTable("products", (id, row) -> new Product(id,
                text(row, "name", true), text(row, "category", true)));
        tables.rows("prices").forEach((id, row) -> {
            Instant end = row.get("valid_to").isNull() ? null : time(row, "valid_to");
            reference(products, row, "product_id").addPrice(new Price(id, nonnegativeLong(row, "price_value"),
                    time(row, "valid_from"), end));
        });
        tables.rows("stocks").forEach((id, row) -> {
            Stock stock = new Stock(nonnegativeInt(row, "available_quantity"), time(row, "updated_at"));
            reference(products, row, "product_id").attachStock(stock);
            stocks.put(id, stock);
        });
        return products;
    }

    private Map<Long, Request> readRequests(Map<Long, Client> clients, Map<Long, Address> addresses) {
        return readTable("requests", (id, row) -> {
            String status = text(row, "status", false);
            require(status.equals("new") || ORDER_STATUSES.contains(status), "requests.status: неизвестный статус.");
            Long clientId = positiveLong(row.get("client_id"));
            Long addressId = positiveLong(row.get("delivery_address_id"));
            return new Request(id, time(row, "received_at"), status, clientId, addressId,
                    clients.get(clientId), addresses.get(addressId));
        });
    }

    private Map<Long, RequestItem> readRequestItems(Map<Long, Request> requests, Map<Long, Product> products) {
        return readTable("request_items", (id, row) -> {
            Request request = reference(requests, row, "request_id");
            Long productId = positiveLong(row.get("product_id"));
            RequestItem item = new RequestItem(id, productId, integer(row.get("quantity")),
                    products.get(productId), request);
            request.addItem(item);
            return item;
        });
    }

    private Map<Long, Order> readOrders(Map<Long, Request> requests) {
        return readTable("orders", (id, row) -> {
            Request request = reference(requests, row, "request_id");
            String status = text(row, "status", false);
            require(ORDER_STATUSES.contains(status), "orders.status: неизвестный статус.");
            require(status.equals(request.status()), "Статусы заявки и заказа должны совпадать.");
            Order order = new Order(id, request, status, time(row, "created_at"), time(row, "updated_at"));
            request.attachOrder(order);
            return order;
        });
    }

    private Map<Long, OrderItem> readOrderItems(Map<Long, RequestItem> sources, Map<Long, Order> orders) {
        return readTable("order_items", (id, row) -> {
            Order order = reference(orders, row, "order_id");
            RequestItem source = reference(sources, row, "request_item_id");
            require(source.request() == order.request(), "order_items: позиция принадлежит другой заявке.");
            Long price = row.get("price").isNull() ? null : nonnegativeLong(row, "price");
            OrderItem item = new OrderItem(id, order, source, price);
            order.addItem(item);
            return item;
        });
    }

    private Map<Long, Reservation> readReservations(Map<Long, OrderItem> items) {
        return readTable("reservations", (id, row) -> {
            OrderItem item = reference(items, row, "order_item_id");
            Integer quantity = integer(row.get("reserved_quantity"));
            require(quantity != null && quantity > 0 && quantity.equals(item.source().quantity()),
                    "reservations: количество не совпадает с позицией заявки.");
            require(item.order().status().equals("confirmed"),
                    "reservations: резерв допустим только для подтвержденного заказа.");
            Reservation result = new Reservation(id, item, quantity, time(row, "reserved_at"));
            item.attachReservation(result);
            return result;
        });
    }

    private void validateOrders(Map<Long, Request> requests, Map<Long, Order> orders) {
        for (Order order : orders.values()) {
            List<OrderItem> items = order.items();
            require(!items.isEmpty() && items.size() == order.request().items().size(),
                    "orders: состав заказа не совпадает с заявкой.");
            if (order.status().equals("confirmed")) {
                for (OrderItem item : items) {
                    require(item.price() != null && item.reservation() != null
                                    && item.source().hasValidQuantity() && item.source().product() != null,
                            "orders: подтвержденный заказ должен иметь цены и полный резерв.");
                }
            }
        }
        for (Request request : requests.values()) {
            require(!(request.isFinal() || request.status().equals("awaiting_price")) || request.order() != null,
                    "Окончательный статус заявки требует существующего заказа.");
        }
    }

    private List<CheckResult> readChecks(Map<Long, Request> requests, Map<Long, OrderItem> items,
                                         Map<Long, CheckType> types) {
        List<CheckResult> checks = new ArrayList<>();
        tables.rows("check_results").forEach((id, row) -> {
            boolean requestNull = row.get("request_id").isNull();
            boolean itemNull = row.get("order_item_id").isNull();
            require(requestNull != itemNull, "check_results: заполните ровно одну ссылку на заявку или позицию.");
            Request request = requestNull ? null : reference(requests, row, "request_id");
            OrderItem item = itemNull ? null : reference(items, row, "order_item_id");
            JsonNode passed = row.get("is_passed");
            require(passed.isBoolean(), "check_results.is_passed: ожидается true или false.");
            checks.add(new CheckResult(id, reference(types, row, "check_type_id"), request, item,
                    passed.booleanValue(), text(row, "message", true), time(row, "checked_at")));
        });
        return checks;
    }

    private <T> Map<Long, T> readTable(String table, BiFunction<Long, ObjectNode, T> read) {
        Map<Long, T> result = new LinkedHashMap<>();
        tables.rows(table).forEach((id, row) -> result.put(id, read.apply(id, row)));
        return result;
    }
}
