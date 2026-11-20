package ru.mirea.orders;

final class OrderItem {
    private final long orderItemId;
    private final RequestItem source;
    private final Order order;
    private Long price;
    private Reservation reservation;

    OrderItem(long orderItemId, Order order, RequestItem source, Long price) {
        this.orderItemId = orderItemId;
        this.order = order;
        this.source = source;
        this.price = price;
    }

    long id() { return orderItemId; }
    Order order() { return order; }
    RequestItem source() { return source; }
    Long price() { return price; }
    Reservation reservation() { return reservation; }

    public void fixPrice(long unitPrice) {
        price = unitPrice;
    }

    public long subtotal() {
        return Math.multiplyExact(price, source.quantity());
    }

    void attachReservation(Reservation value) {
        if (reservation != null) {
            throw new IllegalArgumentException("reservations.order_item_id: повтор значения.");
        }
        reservation = value;
    }
}
