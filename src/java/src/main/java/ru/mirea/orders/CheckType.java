package java.src.main.java.ru.mirea.orders;

final class CheckType {
    private final long checkTypeId;
    private final String code;
    private final String name;

    CheckType(long checkTypeId, String code, String name) {
        this.checkTypeId = checkTypeId;
        this.code = code;
        this.name = name;
    }

    long id() { return checkTypeId; }
    String code() { return code; }

    public boolean matches(String code) {
        return this.code.equals(code);
    }
}
