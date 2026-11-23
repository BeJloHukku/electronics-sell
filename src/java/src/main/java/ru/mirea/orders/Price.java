package java.src.main.java.ru.mirea.orders;

import java.time.Instant;

final class Price {
    private final long priceId;
    private final long priceValue;
    private final Instant validFrom;
    private final Instant validTo;

    Price(long priceId, long priceValue, Instant validFrom, Instant validTo) {
        if (validTo != null && !validFrom.isBefore(validTo)) {
            throw new IllegalArgumentException("prices: конец периода должен быть позже начала.");
        }
        this.priceId = priceId;
        this.priceValue = priceValue;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    long value() {
        return priceValue;
    }

    public boolean isActiveAt(Instant at) {
        return !at.isBefore(validFrom) && (validTo == null || at.isBefore(validTo));
    }
}
