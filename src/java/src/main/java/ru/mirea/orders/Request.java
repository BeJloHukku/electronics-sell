package ru.mirea.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class Request {
    private final long requestId;
    private final Instant receivedAt;
    private final Long clientId;
    private final Long deliveryAddressId;
    private final Client client;
    private final Address address;
    private final List<RequestItem> items = new ArrayList<>();
    private String status;
    private Order order;

    Request(long requestId, Instant receivedAt, String status, Long clientId,
            Long deliveryAddressId, Client client, Address address) {
        this.requestId = requestId;
        this.receivedAt = receivedAt;
        this.status = status;
        this.clientId = clientId;
        this.deliveryAddressId = deliveryAddressId;
        this.client = client;
        this.address = address;
    }

    long id() { return requestId; }
    Long clientId() { return clientId; }
    Long deliveryAddressId() { return deliveryAddressId; }
    Client client() { return client; }
    Address address() { return address; }
    Order order() { return order; }
    String status() { return status; }
    List<RequestItem> items() { return List.copyOf(items); }

    void addItem(RequestItem item) {
        items.add(item);
    }

    void attachOrder(Order value) {
        if (order != null) {
            throw new IllegalArgumentException("orders.request_id: повтор значения.");
        }
        order = value;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public void changeStatus(String status) {
        this.status = status;
    }

    boolean isFinal() {
        return status.equals("confirmed") || status.equals("rejected");
    }
}
