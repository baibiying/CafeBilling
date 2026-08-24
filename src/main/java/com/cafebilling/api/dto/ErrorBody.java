package com.cafebilling.api.dto;

import java.util.List;

public record ErrorBody(String code, String message, List<ErrorDetailResponse> details) {}
