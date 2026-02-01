package com.mg.booth.device;

import java.time.Instant;

/**
 * 设备身份信息（device.json 对应结构）
 */
public class DeviceIdentity {
  private String platformBaseUrl;
  private String deviceCode;
  private String secret;
  private Long deviceId;
  private String deviceToken;
  private Instant tokenExpiresAt;

  public String getPlatformBaseUrl() {
    return platformBaseUrl;
  }

  public void setPlatformBaseUrl(String platformBaseUrl) {
    this.platformBaseUrl = platformBaseUrl;
  }

  public String getDeviceCode() {
    return deviceCode;
  }

  public void setDeviceCode(String deviceCode) {
    this.deviceCode = deviceCode;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public Long getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(Long deviceId) {
    this.deviceId = deviceId;
  }

  public String getDeviceToken() {
    return deviceToken;
  }

  public void setDeviceToken(String deviceToken) {
    this.deviceToken = deviceToken;
  }

  public Instant getTokenExpiresAt() {
    return tokenExpiresAt;
  }

  public void setTokenExpiresAt(Instant tokenExpiresAt) {
    this.tokenExpiresAt = tokenExpiresAt;
  }
}
