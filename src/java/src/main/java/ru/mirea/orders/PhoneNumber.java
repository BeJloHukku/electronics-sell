package java.src.main.java.ru.mirea.orders;

final class PhoneNumber extends Contact {
    private final long phoneId;
    private final String phoneNumber;

    PhoneNumber(long phoneId, String phoneNumber) {
        this.phoneId = phoneId;
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String normalizedValue() {
        return trim(phoneNumber).replaceAll("(?U)\\s+", " ");
    }
}
