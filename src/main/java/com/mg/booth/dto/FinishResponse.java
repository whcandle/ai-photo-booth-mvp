package com.mg.booth.dto;

public class FinishResponse {
  private boolean ok;
  private String status;

  public FinishResponse(boolean ok, String status) {
    this.ok = ok;
    this.status = status;
  }

  public boolean isOk() { return ok; }
  public String getStatus() { return status; }
}
