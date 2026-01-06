package com.mg.booth.exception;

import com.mg.booth.dto.ApiError;
import com.mg.booth.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    ApiError err = new ApiError(e.getCode(), e.getMessage(), null);
    return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(err));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    Map<String, Object> detail = new HashMap<>();
    detail.put("fieldErrors", e.getBindingResult().getFieldErrors().stream().map(fe -> {
      Map<String, Object> m = new HashMap<>();
      m.put("field", fe.getField());
      m.put("message", fe.getDefaultMessage());
      return m;
    }).toList());

    ApiError err = new ApiError("VALIDATION_ERROR", "Request validation failed", detail);
    return ResponseEntity.badRequest().body(new ErrorResponse(err));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleOther(Exception e) {
    // Day1 先兜底，后续你可以加 log + traceId
    ApiError err = new ApiError("INTERNAL_ERROR", "Unexpected error", null);
    return ResponseEntity.internalServerError().body(new ErrorResponse(err));
  }
}
