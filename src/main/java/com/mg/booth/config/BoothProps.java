package com.mg.booth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booth")
public class BoothProps {

  private String deviceId;
  private String gatewayBaseUrl;
  private String sharedRawBaseDir;
  private String cameraAgentBaseUrl;
  
  // ====== Platform API 配置 ======
  private String platformBaseUrl = "http://127.0.0.1:8080";
  private String deviceIdentityFile = "device.json";

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public String getGatewayBaseUrl() {
    return gatewayBaseUrl;
  }

  public void setGatewayBaseUrl(String gatewayBaseUrl) {
    this.gatewayBaseUrl = gatewayBaseUrl;
  }

  public String getSharedRawBaseDir() {
    return sharedRawBaseDir;
  }

  public void setSharedRawBaseDir(String sharedRawBaseDir) {
    this.sharedRawBaseDir = sharedRawBaseDir;
  }

  public String getCameraAgentBaseUrl() {
    return cameraAgentBaseUrl;
  }

  public void setCameraAgentBaseUrl(String cameraAgentBaseUrl) {
    this.cameraAgentBaseUrl = cameraAgentBaseUrl;
  }

  public String getPlatformBaseUrl() {
    return platformBaseUrl;
  }

  public void setPlatformBaseUrl(String platformBaseUrl) {
    this.platformBaseUrl = platformBaseUrl;
  }

  public String getDeviceIdentityFile() {
    return deviceIdentityFile;
  }

  public void setDeviceIdentityFile(String deviceIdentityFile) {
    this.deviceIdentityFile = deviceIdentityFile;
  }
}

