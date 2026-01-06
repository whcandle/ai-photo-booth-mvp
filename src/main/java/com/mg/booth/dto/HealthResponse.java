package com.mg.booth.dto;

import java.time.OffsetDateTime;

public class HealthResponse {
  private boolean ok;
  private String version;
  private OffsetDateTime time;

  public HealthResponse(boolean ok, String version, OffsetDateTime time) {
    this.ok = ok;
    this.version = version;
    this.time = time;
  }

  public boolean isOk() { return ok; }
  public String getVersion() { return version; }
  public OffsetDateTime getTime() { return time; }
}
