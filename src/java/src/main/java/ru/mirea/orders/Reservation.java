package java.src.main.java.ru.mirea.orders;

import java.time.Instant;

final class Reservation {
    private final long reservationId;
    private final int reservedQuantity;
    private final Instant reservedAt;
    private final OrderItem item;

    Reservation(long reservationId, OrderItem item, int reservedQuantity, Instant reservedAt) {
        this.reservationId = reservationId;
        this.item = item;
        this.reservedQuantity = reservedQuantity;
        this.reservedAt = reservedAt;
    }

    long id() { return reservationId; }
    OrderItem item() { return item; }
    int quantity() { return reservedQuantity; }
    Instant reservedAt() { return reservedAt; }

    public boolean covers(int quantity) {
        return reservedQuantity == quantity;
    }
}
