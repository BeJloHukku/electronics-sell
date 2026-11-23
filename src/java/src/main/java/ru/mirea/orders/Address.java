package java.src.main.java.ru.mirea.orders;

import java.util.ArrayList;
import java.util.List;

final class Address {
    private final long addressId;
    private final String country;
    private final String city;
    private final String street;
    private final String house;

    Address(long addressId, String country, String city, String street, String house) {
        this.addressId = addressId;
        this.country = country;
        this.city = city;
        this.street = street;
        this.house = house;
    }

    public List<String> missingFields() {
        List<String> fields = new ArrayList<>();
        if (Contact.trim(country).isEmpty()) fields.add("country");
        if (Contact.trim(city).isEmpty()) fields.add("city");
        if (Contact.trim(street).isEmpty()) fields.add("street");
        if (Contact.trim(house).isEmpty()) fields.add("house");
        return fields;
    }
}
