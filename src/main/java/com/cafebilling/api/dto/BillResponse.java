package com.cafebilling.api.dto;

import java.util.List;

public record BillResponse(
    String currency,
    List<BillLineResponse> lines,
    String subtotal,
    DiscountResponse discount,
    String finalAmount) {}
