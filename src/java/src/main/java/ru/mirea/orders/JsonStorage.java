package ru.mirea.orders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

final class JsonStorage {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Path path;

    JsonStorage(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    ShopData load() throws IOException {
        return new ModelReader(new JsonTables(readJson(path))).read();
    }

    RequestResult process(long id, Instant at) throws IOException {
        if (id <= 0) {
            throw new IllegalArgumentException("Номер заявки должен быть положительным целым числом.");
        }
        Path target = path.toRealPath();
        Path lockPath = target.resolveSibling(target.getFileName() + ".java.lock");
        try (FileChannel guard = FileChannel.open(lockPath, CREATE, WRITE);
             FileLock lock = guard.tryLock()) {
            if (lock == null) {
                throw new IllegalArgumentException("Хранилище уже обрабатывает другой процесс.");
            }
            JsonNode root = readJson(target);
            JsonTables tables = new JsonTables(root);
            ModelReader reader = new ModelReader(tables);
            ShopData data = reader.read();
            Request request = data.request(id);
            if (request.isFinal()) {
                return new RequestResult(request, data.checks());
            }
            OrderProcessingService service = new OrderProcessingService(data,
                    new RequestDataCheck(data.checkType("request_data")),
                    new StockAvailabilityCheck(data.checkType("stock")));
            service.processRequest(request, at);
            RequestResult result = new RequestResult(request, data.checks());
            applyChanges((ObjectNode) root, tables, reader.stocks(), data, request);
            new ModelReader(new JsonTables(root)).read();
            save(target, root);
            return result;
        } catch (OverlappingFileLockException error) {
            throw new IllegalArgumentException("Хранилище уже обрабатывает другой процесс.", error);
        }
    }

    private static JsonNode readJson(Path path) throws IOException {
        return JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static void applyChanges(ObjectNode root, JsonTables tables, Map<Long, Stock> stocks,
                                     ShopData data, Request request) {
        tables.rows("requests").get(request.id()).put("status", request.status());
        for (Map.Entry<Long, Stock> entry : stocks.entrySet()) {
            ObjectNode row = tables.rows("stocks").get(entry.getKey());
            Stock stock = entry.getValue();
            if (row.get("available_quantity").intValue() != stock.availableQuantity()) {
                row.put("available_quantity", stock.availableQuantity());
                row.put("updated_at", Times.utc(stock.updatedAt()));
            }
        }
        Order order = request.order();
        if (order != null) {
            ObjectNode row = tables.rows("orders").get(order.id());
            if (row == null) {
                row = root.withArray("orders").addObject();
                row.put("order_id", order.id());
                row.put("request_id", request.id());
                row.put("created_at", Times.utc(order.createdAt()));
            }
            row.put("status", order.status());
            row.put("updated_at", Times.utc(order.updatedAt()));

            for (OrderItem item : order.items()) {
                ObjectNode line = tables.rows("order_items").get(item.id());
                if (line == null) {
                    line = root.withArray("order_items").addObject();
                    line.put("order_item_id", item.id());
                    line.put("order_id", order.id());
                    line.put("request_item_id", item.source().id());
                }
                line.put("price", item.price());
            }
        }
        for (Reservation reservation : data.reservations()) {
            if (!tables.rows("reservations").containsKey(reservation.id())) {
                root.withArray("reservations").addObject()
                        .put("reservation_id", reservation.id())
                        .put("order_item_id", reservation.item().id())
                        .put("reserved_quantity", reservation.quantity())
                        .put("reserved_at", Times.utc(reservation.reservedAt()));
            }
        }
        for (CheckResult check : data.checks()) {
            if (!tables.rows("check_results").containsKey(check.id())) {
                Long requestId = check.request() == null ? null : check.request().id();
                Long orderItemId = check.orderItem() == null ? null : check.orderItem().id();
                root.withArray("check_results").addObject()
                        .put("check_result_id", check.id())
                        .put("request_id", requestId)
                        .put("check_type_id", check.type().id())
                        .put("order_item_id", orderItemId)
                        .put("is_passed", check.isPassed())
                        .put("message", check.message())
                        .put("checked_at", Times.utc(check.checkedAt()));
            }
        }
    }

    private static void save(Path target, JsonNode data) throws IOException {
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n";
        Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(json);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
