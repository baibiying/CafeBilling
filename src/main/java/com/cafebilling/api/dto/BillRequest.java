package com.cafebilling.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BillRequest(@NotNull List<@Valid BillItemRequest> items) {}
