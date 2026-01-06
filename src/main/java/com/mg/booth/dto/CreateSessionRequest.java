package com.mg.booth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;


/**
 * Request DTO for creating a new session.
 */
public class CreateSessionRequest {

  @NotBlank
  private String deviceId;

  @Min(0) @Max(10)
  private Integer countdownSeconds = 3;

  @Min(0) @Max(5)
  private Integer maxRetries = 2;

  public String getDeviceId() { return deviceId; }
  public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

  public Integer getCountdownSeconds() { return countdownSeconds; }
  public void setCountdownSeconds(Integer countdownSeconds) { this.countdownSeconds = countdownSeconds; }

  public Integer getMaxRetries() { return maxRetries; }
  public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
}
