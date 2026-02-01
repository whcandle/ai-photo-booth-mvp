package com.mg.booth.platform.dto;

public class HandshakeData {
  private Long deviceId;
  private String deviceToken;
  private Long expiresIn;
  private String serverTime;

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

  public Long getExpiresIn() {
    return expiresIn;
  }

  public void setExpiresIn(Long expiresIn) {
    this.expiresIn = expiresIn;
  }

  public String getServerTime() {
    return serverTime;
  }

  public void setServerTime(String serverTime) {
    this.serverTime = serverTime;
  }
}
