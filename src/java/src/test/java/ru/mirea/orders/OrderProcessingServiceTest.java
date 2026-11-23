package java.src.test.java.ru.mirea.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderProcessingServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long REQUEST_ID = 101;
    private static final Instant AT = Instant.parse("2026-11-01T09:00:00Z");

    @TempDir
    Path temporary;

    @Test
    void confirmedRequestKeepsTheSameReservationOnRetry() throws IOException {
        Path path = save(newRequests());
        JsonStorage storage = new JsonStorage(path);

        RequestResult first = storage.process(REQUEST_ID, AT);
        assertEquals("confirmed", first.status());
        assertEquals(6_497_000L, first.total().longValue());
        JsonNode data = JSON.readTree(path.toFile());
        ArrayNode reservations = (ArrayNode) data.get("reservations");
        assertEquals(2, reservations.size());
        assertEquals(1, reservations.get(0).get("reserved_quantity").intValue());
        assertEquals(2, reservations.get(1).get("reserved_quantity").intValue());
        assertEquals(1, objectById((ArrayNode) data.get("stocks"), "product_id", 1)
                .get("available_quantity").intValue());
        assertEquals(1, objectById((ArrayNode) data.get("stocks"), "product_id", 2)
                .get("available_quantity").intValue());
        byte[] saved = Files.readAllBytes(path);

        RequestResult retry = storage.process(REQUEST_ID, AT.plusSeconds(60));

        assertEquals("confirmed", retry.status());
        assertEquals(first.orderId(), retry.orderId());
        assertEquals(first.total(), retry.total());
        assertArrayEquals(saved, Files.readAllBytes(path));
    }

    @Test
    void insufficientCombinedStockLeavesInventoryUntouched() throws IOException {
        ObjectNode input = newRequests();
        objectById(input.withArray("request_items"), "request_item_id", 2).put("product_id", 1);

        assertUnreserved(input, "rejected", "требуется 3, доступно 2");
    }

    @Test
    void missingPriceForSecondItemLeavesAllPricesAndStockUntouched() throws IOException {
        ObjectNode input = newRequests();
        ArrayNode prices = input.withArray("prices");
        for (int i = prices.size() - 1; i >= 0; i--) {
            if (prices.get(i).get("product_id").longValue() == 2) {
                prices.remove(i);
            }
        }

        assertUnreserved(input, "awaiting_price", "нет единственной действующей цены");
    }

    @Test
    void overlappingPricesLeaveAllPricesAndStockUntouched() throws IOException {
        ObjectNode input = newRequests();
        input.withArray("prices").addObject()
                .put("price_id", 5)
                .put("product_id", 2)
                .put("price_value", 600_000)
                .put("valid_from", "2026-11-01T00:00:00Z")
                .putNull("valid_to");

        assertUnreserved(input, "awaiting_price", "нет единственной действующей цены");
    }

    private void assertUnreserved(ObjectNode input, String status, String message) throws IOException {
        Path path = save(input);
        RequestResult result = new JsonStorage(path).process(REQUEST_ID, AT);

        assertEquals(status, result.status());
        assertNull(result.total());
        assertTrue(result.message().contains(message), result.message());
        JsonNode saved = JSON.readTree(path.toFile());
        assertEquals(input.get("stocks"), saved.get("stocks"));
        assertTrue(saved.get("reservations").isEmpty());
        JsonNode orderItems = saved.get("order_items");
        assertEquals(2, orderItems.size());
        for (JsonNode item : orderItems) {
            assertTrue(item.get("price").isNull());
        }
    }

    private Path save(ObjectNode data) throws IOException {
        Path path = temporary.resolve("data.json");
        JSON.writeValue(path.toFile(), data);
        return path;
    }

    private static ObjectNode newRequests() throws IOException {
        ObjectNode data = (ObjectNode) JSON.readTree(Path.of("data.json").toFile());
        for (JsonNode request : data.withArray("requests")) {
            ((ObjectNode) request).put("status", "new");
        }
        for (String table : List.of("orders", "order_items", "reservations", "check_results")) {
            data.withArray(table).removeAll();
        }
        return data;
    }

    private static ObjectNode objectById(ArrayNode rows, String field, long id) {
        for (JsonNode row : rows) {
            if (row.get(field).longValue() == id) {
                return (ObjectNode) row;
            }
        }
        throw new AssertionError("В демонстрационных данных отсутствует " + field + " = " + id);
    }
}
