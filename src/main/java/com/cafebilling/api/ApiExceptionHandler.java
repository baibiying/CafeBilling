package com.cafebilling.api;

import com.cafebilling.api.dto.ErrorBody;
import com.cafebilling.api.dto.ErrorDetailResponse;
import com.cafebilling.api.dto.ErrorResponse;
import com.cafebilling.billing.BillingException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(BillingException.class)
  public ResponseEntity<ErrorResponse> handleBilling(BillingException ex) {
    List<ErrorDetailResponse> details =
        ex.details().stream()
            .map(
                detail -> new ErrorDetailResponse(detail.field(), detail.issue(), detail.message()))
            .toList();
    return badRequest("The bill request is invalid.", details);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<ErrorDetailResponse> details = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      String field = error.getField();
      String issue = field.contains("quantity") ? "INVALID_QUANTITY" : "INVALID_REQUEST";
      String message =
          error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage();
      details.add(new ErrorDetailResponse(field, issue, message));
    }
    return badRequest("The bill request is invalid.", details);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return badRequest(
        "The bill request is invalid.",
        List.of(
            new ErrorDetailResponse(
                "request", "INVALID_REQUEST", "Request body is invalid JSON.")));
  }

  private static ResponseEntity<ErrorResponse> badRequest(
      String message, List<ErrorDetailResponse> details) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(new ErrorBody("VALIDATION_ERROR", message, details)));
  }
}
