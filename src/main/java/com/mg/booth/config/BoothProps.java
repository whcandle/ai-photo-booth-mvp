package com.mg.booth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booth")
public class BoothProps {

  private static final Logger log = LoggerFactory.getLogger(BoothProps.class);

  private String deviceId;
  private String gatewayBaseUrl;
  private String sharedRawBaseDir;
  private String cameraAgentBaseUrl;
  
  // ====== Platform API 配置 ======
  private String platformBaseUrl = "http://127.0.0.1:8080";
  private String deviceIdentityFile = "device.json";
  
  // ====== Deprecated API 开关 ======
  /**
   * 是否启用已废弃的对外 API（如 /api/v1/device/activities 等）
   * 默认关闭，防止旧入口被误用。
   */
  private boolean deprecatedApiEnabled = false;
  
  // ====== Camera 配置 ======
  private String cameraConfigFile = "camera.json";
  
  // ====== Data Directory 配置 ======
  /**
   * 数据目录根路径（用于存储 templates、cache、index 等）
   * 默认：./data
   */
  private String dataDir = "./data";

  // ====== Delivery 配置 ======
  /**
   * 公网可访问的基础 URL（用于生成手机可扫描的下载链接）
   * 不能使用 localhost，必须是手机可访问的地址（如 http://192.168.1.100:8080 或 https://example.com）
   */
  private String publicBaseUrl;

  /**
   * 交付模式配置
   */
  private Delivery delivery = new Delivery();

  public static class Delivery {
    /**
     * 交付模式：local（本地交付）或 cloud（云端交付，预留）
     * 默认：local
     */
    private String mode = "local";

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode != null ? mode.trim() : "local";
    }
  }

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

  public String getCameraConfigFile() {
    return cameraConfigFile;
  }

  public void setCameraConfigFile(String cameraConfigFile) {
    this.cameraConfigFile = cameraConfigFile;
  }

  public boolean isDeprecatedApiEnabled() {
    return deprecatedApiEnabled;
  }

  public void setDeprecatedApiEnabled(boolean deprecatedApiEnabled) {
    this.deprecatedApiEnabled = deprecatedApiEnabled;
  }

  public String getDataDir() {
    return dataDir;
  }

  public void setDataDir(String dataDir) {
    this.dataDir = dataDir;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    if (publicBaseUrl != null) {
      this.publicBaseUrl = publicBaseUrl.trim();
    } else {
      this.publicBaseUrl = null;
    }
    // 空值允许但要 warn 日志提示未配置
    if (this.publicBaseUrl == null || this.publicBaseUrl.isBlank()) {
      log.warn("[BoothProps] publicBaseUrl is not configured. Download links may not be accessible from mobile devices.");
    }
  }

  public Delivery getDelivery() {
    return delivery;
  }

  public void setDelivery(Delivery delivery) {
    this.delivery = delivery != null ? delivery : new Delivery();
  }
}

