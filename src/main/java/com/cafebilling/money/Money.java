package com.cafebilling.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final String CURRENCY = "CNY";
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {}

    public static BigDecimal of(int value) {
        return of(BigDecimal.valueOf(value));
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static String format(BigDecimal value) {
        return of(value).toPlainString();
    }
}
