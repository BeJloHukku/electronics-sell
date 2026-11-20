package ru.mirea.orders;

import java.time.Instant;
import java.util.List;

interface CheckRule {
    List<CheckResult> check(Request request, Instant at);
}
