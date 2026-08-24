package com.cafebilling.billing;

import java.math.BigDecimal;

public record Discount(BigDecimal amount, String description) {}
