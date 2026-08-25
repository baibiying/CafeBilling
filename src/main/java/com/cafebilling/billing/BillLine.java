package com.cafebilling.billing;

import java.math.BigDecimal;

public record BillLine(
    String code, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
