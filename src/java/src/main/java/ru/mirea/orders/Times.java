package ru.mirea.orders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

final class Times {
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter MICROSECOND =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");

    private Times() {}

    static Instant parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Дата должна быть строкой ISO 8601 с часовым поясом.");
        }
        String value = input;
        if (value.length() > 10 && value.charAt(10) == ' ') {
            value = value.substring(0, 10) + 'T' + value.substring(11);
        }
        if (value.length() > 15 && value.matches(".*[+-][0-9]{4}$")) {
            value = value.substring(0, value.length() - 2) + ':' + value.substring(value.length() - 2);
        }
        try {
            return OffsetDateTime.parse(value.replace(',', '.')).toInstant().truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Некорректная дата или отсутствует часовой пояс: '" + input + "'.");
        }
    }

    static String utc(Instant at) {
        OffsetDateTime date = at.atOffset(ZoneOffset.UTC);
        DateTimeFormatter formatter = at.getNano() == 0 ? SECOND : MICROSECOND;
        return formatter.format(date) + "+00:00";
    }
}
