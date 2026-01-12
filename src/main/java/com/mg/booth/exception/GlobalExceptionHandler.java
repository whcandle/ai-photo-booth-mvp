package com.mg.booth.exception;

import com.mg.booth.dto.ApiError;
import com.mg.booth.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
    ApiError err = new ApiError("NOT_FOUND", e.getMessage(), null);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(err));
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
    ApiError err = new ApiError(e.getCode(), e.getMessage(), null);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(err));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    ApiError err = new ApiError(e.getCode(), e.getMessage(), null);
    return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(err));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    Map<String, Object> detail = new LinkedHashMap<>();
    for (FieldError fe : e.getBindingResult().getFieldErrors()) {
      detail.put(fe.getField(), fe.getDefaultMessage());
    }

    ApiError err = new ApiError("BAD_REQUEST", "Validation failed", detail);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(err));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleOther(Exception e) {
    // Day7: 不把堆栈返回给前端（服务端日志看即可）
    Map<String, Object> detail = Map.of(
      "type", e.getClass().getSimpleName()
    );
    ApiError err = new ApiError("INTERNAL_ERROR", "Unexpected error", detail);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(err));
  }
}
