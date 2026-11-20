package ru.mirea.orders;

final class RequestItem {
    private final long requestItemId;
    private final Long productId;
    private final Integer quantity;
    private final Product product;
    private final Request request;

    RequestItem(long requestItemId, Long productId, Integer quantity, Product product, Request request) {
        this.requestItemId = requestItemId;
        this.productId = productId;
        this.quantity = quantity;
        this.product = product;
        this.request = request;
    }

    long id() { return requestItemId; }
    Long productId() { return productId; }
    Integer quantity() { return quantity; }
    Product product() { return product; }
    Request request() { return request; }

    public boolean hasValidQuantity() {
        return quantity != null && quantity > 0;
    }
}
