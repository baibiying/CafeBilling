package com.cafebilling.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BillItemRequest(@NotBlank String code, @NotNull Integer quantity) {}
