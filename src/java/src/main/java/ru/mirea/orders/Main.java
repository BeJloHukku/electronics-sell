package ru.mirea.orders;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class Main {
    private static final Map<String, String> STATUS_NAMES = Map.of(
            "new", "Ожидает обработки",
            "needs_clarification", "Требует уточнения",
            "awaiting_price", "Ожидает уточнения цены",
            "confirmed", "Подтвержден",
            "rejected", "Отклонен");

    private Main() {}

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException error) {
            System.err.println("Ошибка: " + error.getMessage());
            return 2;
        }
        if (options.help) {
            System.out.println("Проверка и подтверждение заказов бытовой электроники");
            System.out.println("electronics-orders [--data ФАЙЛ]");
            System.out.println("    [--list | --show НОМЕР | --request-id НОМЕР [--at ISO-8601]]");
            return 0;
        }
        try {
            JsonStorage storage = new JsonStorage(options.path);
            if (options.action == Action.PROCESS) {
                Instant at = options.at == null ? Instant.now() : Times.parse(options.at);
                printResult(storage.process(options.id, at));
            } else {
                ShopData data = storage.load();
                if (options.action == Action.SHOW) {
                    printResult(new RequestResult(data.request(options.id), data.checks()));
                } else {
                    printList(data.requests());
                }
            }
            return 0;
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("Ошибка: " + error.getMessage());
        } catch (ArithmeticException error) {
            System.err.println("Ошибка: значение выходит за диапазон целых чисел Java.");
        }
        return 1;
    }

    private static void printList(Collection<Request> requests) {
        if (requests.isEmpty()) {
            System.out.println("Заявок нет.");
        }
        for (Request request : requests) {
            System.out.println(request.id() + ": " + request.status());
        }
    }

    private static void printResult(RequestResult result) {
        System.out.println("Заявка: " + result.id());
        System.out.println("Статус: " + STATUS_NAMES.get(result.status()));
        if (result.orderId() != null) {
            System.out.println("Заказ: " + result.orderId());
        }
        if (result.total() != null) {
            long total = result.total();
            String money = String.format(Locale.ROOT, "%,d,%02d руб.", total / 100, total % 100);
            int decimalSeparator = money.lastIndexOf(',');
            System.out.println("Сумма: " + money.substring(0, decimalSeparator).replace(',', ' ')
                    + money.substring(decimalSeparator));
        }
        System.out.println(result.message());
    }

    private enum Action { LIST, SHOW, PROCESS }

    private static final class Options {
        private Path path = Path.of("data.json");
        private Action action;
        private long id;
        private String at;
        private boolean help;

        private static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                int equals = argument.indexOf('=');
                String key = equals < 0 ? argument : argument.substring(0, equals);
                switch (key) {
                    case "-h":
                    case "--help":
                        options.help = true;
                        return options;
                    case "--list":
                        if (equals >= 0) {
                            throw new IllegalArgumentException("--list не принимает значение.");
                        }
                        options.select(Action.LIST);
                        break;
                    case "--data":
                    case "--request-id":
                    case "--show":
                    case "--at":
                        String value;
                        if (equals >= 0) {
                            value = argument.substring(equals + 1);
                        } else {
                            if (i + 1 == args.length || args[i + 1].startsWith("--")) {
                                throw new IllegalArgumentException("Нужно указать значение " + key + ".");
                            }
                            value = args[++i];
                        }
                        options.setValue(key, value);
                        break;
                    default:
                        throw new IllegalArgumentException("Неизвестный параметр " + key + ".");
                }
            }
            if (options.at != null && options.action != Action.PROCESS) {
                throw new IllegalArgumentException("--at используется только вместе с --request-id.");
            }
            if (options.action == null) {
                options.action = Action.LIST;
            }
            return options;
        }

        private void setValue(String key, String value) {
            switch (key) {
                case "--data":
                    path = Path.of(value);
                    break;
                case "--at":
                    at = value;
                    break;
                default:
                    select(key.equals("--show") ? Action.SHOW : Action.PROCESS);
                    try {
                        id = Long.parseLong(value);
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException(key + ": ожидается целый номер заявки.");
                    }
            }
        }

        private void select(Action value) {
            if (action != null && action != value) {
                throw new IllegalArgumentException("Выберите один режим: --list, --show или --request-id.");
            }
            action = value;
        }
    }
}
