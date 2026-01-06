package com.mg.booth.dto;

import java.util.Map;

public class ApiError {
  private String code;
  private String message;
  private Map<String, Object> detail;

  public ApiError(String code, String message, Map<String, Object> detail) {
    this.code = code;
    this.message = message;
    this.detail = detail;
  }

  public String getCode() { return code; }
  public String getMessage() { return message; }
  public Map<String, Object> getDetail() { return detail; }
}
