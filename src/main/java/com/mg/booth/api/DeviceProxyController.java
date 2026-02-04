package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.DeviceConfig;
import com.mg.booth.device.DeviceConfigStore;
import com.mg.booth.device.PlatformDeviceApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备代理 Controller
 * 为 kiosk 提供本地代理接口，无需直接连接 platform
 * 
 * 注意：使用 DeviceConfigStore 读取 device.json（单一真源）
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceProxyController {

  private static final Logger log = LoggerFactory.getLogger(DeviceProxyController.class);

  private final BoothProps props;
  private final DeviceConfigStore configStore;
  private final PlatformDeviceApiClient client;

  public DeviceProxyController(
      BoothProps props,
      DeviceConfigStore configStore,
      @Qualifier("devicePlatformDeviceApiClient") PlatformDeviceApiClient client) {
    this.props = props;
    this.configStore = configStore;
    this.client = client;
  }

  /**
   * 获取设备活动列表（代理 platform 接口）
   * 
   * GET /api/v1/device/activities
   * 
   * @return 统一响应格式：{success, data, message}
   */
  @GetMapping("/activities")
  public Map<String, Object> getActivities() {
    try {
      // 1. 加载 device.json（使用 DeviceConfigStore）
      Path file = Path.of(props.getDeviceIdentityFile());
      DeviceConfig config = configStore.load(file);

      // 2. 检查 deviceId 和 token
      if (config.getDeviceId() == null || config.getDeviceId().isBlank() 
          || config.getDeviceToken() == null || config.getDeviceToken().isBlank()) {
        return createErrorResponse("device not handshaked yet");
      }

      // 3. 获取 platformBaseUrl
      String platformBaseUrl = props.getPlatformBaseUrl();
      if (config.getPlatformBaseUrl() != null && !config.getPlatformBaseUrl().isBlank()) {
        platformBaseUrl = config.getPlatformBaseUrl();
      }

      if (platformBaseUrl == null || platformBaseUrl.isBlank()) {
        return createErrorResponse("platformBaseUrl not configured");
      }

      // 4. 调用 platform API
      Long deviceId = config.getDeviceIdAsLong();
      if (deviceId == null) {
        return createErrorResponse("invalid deviceId");
      }
      
      List<Map<String, Object>> activities = client.listActivities(
          platformBaseUrl, deviceId, config.getDeviceToken());

      // 5. 返回成功响应
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", activities);
      response.put("message", null);
      return response;

    } catch (Exception e) {
      log.error("[device-proxy] Failed to get activities: {}", e.getMessage(), e);
      return createErrorResponse("Failed to fetch activities: " + e.getMessage());
    }
  }

  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("data", null);
    response.put("message", message);
    return response;
  }
}
