package com.mg.booth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booth")
public class BoothProps {

  private String deviceId;
  private String gatewayBaseUrl;
  private String sharedRawBaseDir;
  private String cameraAgentBaseUrl;

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
}

