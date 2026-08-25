package com.cafebilling.billing;

import java.math.BigDecimal;
import java.util.List;

public record Bill(
    List<BillLine> lines,
    BigDecimal subtotal,
    Discount discount,
    BigDecimal finalAmount,
    String currency) {}
