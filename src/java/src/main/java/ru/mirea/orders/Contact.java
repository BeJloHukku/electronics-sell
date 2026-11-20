package ru.mirea.orders;

import java.util.regex.Pattern;

abstract class Contact {
    private static final Pattern TRIM = Pattern.compile(
            "^[\\p{IsWhite_Space}\\x{1c}-\\x{1f}]+|[\\p{IsWhite_Space}\\x{1c}-\\x{1f}]+$");

    public abstract String normalizedValue();

    public boolean isPresent() {
        return !normalizedValue().isEmpty();
    }

    static String trim(String text) {
        return text == null ? "" : TRIM.matcher(text).replaceAll("");
    }
}
