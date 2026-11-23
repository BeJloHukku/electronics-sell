package java.src.main.java.ru.mirea.orders;

final class Email extends Contact {
    private final long emailId;
    private final String email;

    Email(long emailId, String email) {
        this.emailId = emailId;
        this.email = email;
    }

    @Override
    public String normalizedValue() {
        return trim(email);
    }
}
