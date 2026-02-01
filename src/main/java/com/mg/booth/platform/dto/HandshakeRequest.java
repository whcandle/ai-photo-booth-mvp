package com.mg.booth.platform.dto;

public class HandshakeRequest {
  private String deviceCode;
  private String secret;

  public HandshakeRequest() {
  }

  public HandshakeRequest(String deviceCode, String secret) {
    this.deviceCode = deviceCode;
    this.secret = secret;
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
}
