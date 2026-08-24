package com.cafebilling.api.dto;

public record BillLineResponse(
        String code, String name, String unitPrice, int quantity, String lineTotal) {}
