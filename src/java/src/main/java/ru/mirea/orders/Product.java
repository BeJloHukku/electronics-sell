package ru.mirea.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class Product {
    private final long productId;
    private final String name;
    private final String category;
    private final List<Price> prices = new ArrayList<>();
    private Stock stock;

    Product(long productId, String name, String category) {
        this.productId = productId;
        this.name = name;
        this.category = category;
    }

    void addPrice(Price price) {
        prices.add(price);
    }

    void attachStock(Stock value) {
        stock = value;
    }

    Stock stock() {
        return stock;
    }

    public List<Price> activePrices(Instant at) {
        return prices.stream().filter(price -> price.isActiveAt(at)).collect(Collectors.toList());
    }
}
