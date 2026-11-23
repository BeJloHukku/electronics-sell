package java.src.main.java.ru.mirea.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonTables {
    private static final List<String> SCHEMA = List.of(
            "clients client_id first_name middle_name last_name",
            "emails email_id client_id email",
            "phone_numbers phone_id client_id phone_number",
            "addresses address_id country city street house",
            "requests request_id client_id received_at delivery_address_id status",
            "request_items request_item_id request_id product_id quantity",
            "products product_id name category",
            "prices price_id product_id price_value valid_from valid_to",
            "stocks product_id available_quantity updated_at",
            "orders order_id request_id status created_at updated_at",
            "order_items order_item_id order_id request_item_id price",
            "reservations reservation_id order_item_id reserved_quantity reserved_at",
            "check_types check_type_id code name",
            "check_results check_result_id request_id check_type_id order_item_id is_passed message checked_at"
    );

    private final Map<String, Map<Long, ObjectNode>> tables = new LinkedHashMap<>();

    JsonTables(JsonNode root) {
        require(root != null && root.isObject() && root.size() == SCHEMA.size(),
                "Хранилище должно содержать ровно 14 сущностей логической модели.");
        for (String schema : SCHEMA) {
            List<String> words = Arrays.asList(schema.split(" "));
            String name = words.get(0);
            require(root.has(name), "Хранилище должно содержать ровно 14 сущностей логической модели.");
            JsonNode array = root.get(name);
            require(array.isArray(), name + ": ожидается список записей.");
            List<String> fields = words.subList(1, words.size());
            Map<Long, ObjectNode> index = new LinkedHashMap<>();
            for (JsonNode node : array) {
                require(node.isObject() && node.size() == fields.size()
                                && fields.stream().allMatch(node::has),
                        name + ": неверный набор атрибутов; ожидаются " + String.join(", ", fields) + ".");
                Long key = positiveLong(node.get(fields.get(0)));
                require(key != null, name + ": неверный ключ " + node.get(fields.get(0)) + ".");
                require(index.putIfAbsent(key, (ObjectNode) node) == null, name + ": повтор ключа " + key + ".");
            }
            tables.put(name, index);
        }
    }

    Map<Long, ObjectNode> rows(String table) {
        return tables.get(table);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static Long positiveLong(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0
                ? value.longValue() : null;
    }

    static Integer integer(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    static String text(JsonNode row, String field, boolean nullable) {
        JsonNode value = row.get(field);
        require(value.isTextual() || (nullable && value.isNull()), field + ": ожидается строка"
                + (nullable ? " или null." : "."));
        return value.textValue();
    }

    static long nonnegativeLong(JsonNode row, String field) {
        JsonNode value = row.get(field);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0,
                field + ": ожидается целое неотрицательное число в диапазоне long.");
        return value.longValue();
    }

    static int nonnegativeInt(JsonNode row, String field) {
        Integer value = integer(row.get(field));
        require(value != null && value >= 0, field + ": ожидается целое неотрицательное число в диапазоне int.");
        return value;
    }

    static Instant time(JsonNode row, String field) {
        return Times.parse(text(row, field, false));
    }

    static <T> T reference(Map<Long, T> index, JsonNode row, String field) {
        JsonNode value = row.get(field);
        T result = index.get(positiveLong(value));
        require(result != null, field + ": отсутствует запись " + value + ".");
        return result;
    }
}
