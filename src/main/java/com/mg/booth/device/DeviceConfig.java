package com.mg.booth.device;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

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

  // ISO8601 formatter for tokenExpiresAt
  private static final DateTimeFormatter ISO8601_FORMATTER = DateTimeFormatter.ISO_INSTANT;

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

  /**
   * Set deviceId from Long (converts to String)
   */
  public void setDeviceIdFromLong(Long deviceId) {
    this.deviceId = deviceId != null ? String.valueOf(deviceId) : null;
  }

  /**
   * Get deviceId as Long (converts from String)
   */
  @JsonIgnore
  public Long getDeviceIdAsLong() {
    if (deviceId == null || deviceId.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(deviceId);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Set tokenExpiresAt from Instant (converts to ISO8601 String)
   */
  public void setTokenExpiresAtFromInstant(Instant instant) {
    this.tokenExpiresAt = instant != null ? ISO8601_FORMATTER.format(instant) : null;
  }

  /**
   * Get tokenExpiresAt as Instant (parses from ISO8601 String)
   */
  @JsonIgnore
  public Instant getTokenExpiresAtAsInstant() {
    if (tokenExpiresAt == null || tokenExpiresAt.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(tokenExpiresAt);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Check if token is valid (exists and not expired, with 30 seconds buffer)
   */
  @JsonIgnore
  public boolean isTokenValid() {
    if (deviceToken == null || deviceToken.isBlank()) {
      return false;
    }
    Instant expiresAt = getTokenExpiresAtAsInstant();
    if (expiresAt == null) {
      return true; // No expiration time means valid
    }
    return expiresAt.isAfter(Instant.now().plusSeconds(30));
  }
}
