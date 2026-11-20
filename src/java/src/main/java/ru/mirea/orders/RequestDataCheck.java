package ru.mirea.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class RequestDataCheck implements CheckRule {
    private final CheckType type;

    RequestDataCheck(CheckType type) {
        this.type = type;
    }

    @Override
    public List<CheckResult> check(Request request, Instant at) {
        List<String> errors = new ArrayList<>();
        Client client = request.client();
        Address address = request.address();

        if (request.clientId() == null) missing(errors, "client_id");
        if (client == null || !client.hasFirstName()) missing(errors, "first_name");
        if (client == null || !client.hasLastName()) missing(errors, "last_name");
        if (client == null || client.preferredContact().isEmpty()) missing(errors, "contact");
        if (request.deliveryAddressId() == null) missing(errors, "delivery_address_id");

        List<String> addressFields = address == null
                ? List.of("country", "city", "street", "house") : address.missingFields();
        for (String name : addressFields) {
            missing(errors, name);
        }
        if (!request.hasItems()) {
            errors.add("Состав заявки пуст.");
        }
        int position = 0;
        for (RequestItem item : request.items()) {
            position++;
            if (item.productId() == null || !item.hasValidQuantity()) {
                errors.add("Позиция " + position + ": нужен product_id и целое количество больше нуля.");
            }
        }
        boolean passed = errors.isEmpty();
        String message = passed ? "Данные заявки заполнены." : String.join(" ", errors);
        return List.of(new CheckResult(0, type, request, null, passed, message, at));
    }

    private static void missing(List<String> errors, String name) {
        errors.add("Не заполнено поле " + name + ".");
    }
}
