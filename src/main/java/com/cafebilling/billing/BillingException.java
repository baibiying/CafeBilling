package com.cafebilling.billing;

import java.util.List;

public class BillingException extends RuntimeException {

    private final List<ErrorDetail> details;

    public BillingException(String message, List<ErrorDetail> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
