package java.src.main.java.ru.mirea.orders;

import java.time.Instant;

final class Stock {
    private int availableQuantity;
    private Instant updatedAt;

    Stock(int availableQuantity, Instant updatedAt) {
        this.availableQuantity = availableQuantity;
        this.updatedAt = updatedAt;
    }

    int availableQuantity() { return availableQuantity; }
    Instant updatedAt() { return updatedAt; }

    public boolean covers(int quantity) {
        return quantity > 0 && availableQuantity >= quantity;
    }

    public void debit(int quantity, Instant at) {
        if (!covers(quantity)) {
            throw new IllegalArgumentException("Недостаточный остаток для резервирования.");
        }
        availableQuantity -= quantity;
        updatedAt = at;
    }
}
