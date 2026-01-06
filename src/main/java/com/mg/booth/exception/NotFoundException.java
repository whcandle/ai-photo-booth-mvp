package com.mg.booth.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
  public NotFoundException(String message) {
    super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }
}
