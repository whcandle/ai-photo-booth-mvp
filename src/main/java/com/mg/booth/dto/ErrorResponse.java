package com.mg.booth.dto;

public class ErrorResponse {
  private boolean ok = false;
  private ApiError error;

  public ErrorResponse(ApiError error) {
    this.error = error;
  }

  public boolean isOk() { return ok; }
  public ApiError getError() { return error; }
}
