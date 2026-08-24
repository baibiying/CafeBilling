package com.cafebilling.menu;

import java.math.BigDecimal;

public record MenuItem(String code, String name, String category, BigDecimal unitPrice) {}
