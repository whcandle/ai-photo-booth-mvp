package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.DeviceIdentity;
import com.mg.booth.device.DeviceIdentityStore;
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
import java.util.Optional;

/**
 * 设备代理 Controller
 * 为 kiosk 提供本地代理接口，无需直接连接 platform
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceProxyController {

  private static final Logger log = LoggerFactory.getLogger(DeviceProxyController.class);

  private final BoothProps props;
  private final DeviceIdentityStore store;
  private final PlatformDeviceApiClient client;

  public DeviceProxyController(
      BoothProps props,
      @Qualifier("deviceDeviceIdentityStore") DeviceIdentityStore store,
      @Qualifier("devicePlatformDeviceApiClient") PlatformDeviceApiClient client) {
    this.props = props;
    this.store = store;
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
      // 1. 加载 device.json
      Path file = Path.of(props.getDeviceIdentityFile());
      Optional<DeviceIdentity> optId = store.load(file);

      if (optId.isEmpty()) {
        return createErrorResponse("device.json not found");
      }

      DeviceIdentity id = optId.get();

      // 2. 检查 deviceId 和 token
      if (id.getDeviceId() == null || id.getDeviceToken() == null || id.getDeviceToken().isBlank()) {
        return createErrorResponse("device not handshaked yet");
      }

      // 3. 获取 platformBaseUrl
      String platformBaseUrl = props.getPlatformBaseUrl();
      if (id.getPlatformBaseUrl() != null && !id.getPlatformBaseUrl().isBlank()) {
        platformBaseUrl = id.getPlatformBaseUrl();
      }

      if (platformBaseUrl == null || platformBaseUrl.isBlank()) {
        return createErrorResponse("platformBaseUrl not configured");
      }

      // 4. 调用 platform API
      List<Map<String, Object>> activities = client.listActivities(
          platformBaseUrl, id.getDeviceId(), id.getDeviceToken());

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
