package com.mg.booth.platform;

/**
 * Platform device session data (for platform API communication)
 * 
 * Note: This is different from device.json identity.
 * This class represents the session data used for platform API calls.
 * 
 * @deprecated This class is kept for backward compatibility with PlatformSyncService (which is disabled).
 * For device.json operations, use DeviceConfig and DeviceConfigStore instead.
 */
@Deprecated
public class PlatformDeviceSession {
  private String deviceCode;
  private String secret;
  private Long deviceId;
  private String token;

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

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
