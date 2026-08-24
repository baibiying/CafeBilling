package com.cafebilling.api.dto;

import java.util.List;

public record MenuResponse(String currency, List<MenuItemResponse> items) {}
