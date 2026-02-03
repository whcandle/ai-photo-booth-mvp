package com.mg.booth.device;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Device configuration data model (corresponds to device.json)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceConfig {
  
  // Writable fields (can be edited in UI)
  private String platformBaseUrl;
  private String deviceCode;
  private String secret;
  
  // Read-only fields (written back by handshake)
  private String deviceId;
  private String deviceToken;
  private String tokenExpiresAt; // ISO8601 format (UTC or with timezone)

  public DeviceConfig() {
    // Default constructor
  }

  // Getters and Setters
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

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public String getDeviceToken() {
    return deviceToken;
  }

  public void setDeviceToken(String deviceToken) {
    this.deviceToken = deviceToken;
  }

  public String getTokenExpiresAt() {
    return tokenExpiresAt;
  }

  public void setTokenExpiresAt(String tokenExpiresAt) {
    this.tokenExpiresAt = tokenExpiresAt;
  }

  /**
   * Create default config with empty values
   */
  public static DeviceConfig createDefault() {
    DeviceConfig config = new DeviceConfig();
    config.platformBaseUrl = "";
    config.deviceCode = "";
    config.secret = "";
    config.deviceId = null;
    config.deviceToken = null;
    config.tokenExpiresAt = null;
    return config;
  }
}
