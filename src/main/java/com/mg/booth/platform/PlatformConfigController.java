package com.mg.booth.platform;

import com.mg.booth.config.BoothProps;
import com.mg.booth.platform.dto.DeviceActivityDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已废弃的 Platform 配置接口。
 * 早期用于输出平台活动缓存，现已被 /local/device/activities 替代。
 */
@Deprecated
@RestController
public class PlatformConfigController {

  private static final Logger log = LoggerFactory.getLogger(PlatformConfigController.class);

  private final PlatformSyncService sync;
  private final BoothProps props;

  public PlatformConfigController(PlatformSyncService sync, BoothProps props) {
    this.sync = sync;
    this.props = props;
  }

  @GetMapping("/api/v1/platform/activities")
  public ResponseEntity<?> activities() {
    log.warn("[deprecated-api] /api/v1/platform/activities called. This endpoint is deprecated, "
        + "please use /local/device/activities instead.");

    // 关闭废弃 API 时，直接返回 410 Gone，防止误用
    if (!props.isDeprecatedApiEnabled()) {
      Map<String, Object> body = new HashMap<>();
      body.put("success", false);
      body.put("data", null);
      body.put("message",
          "DEPRECATED endpoint /api/v1/platform/activities is disabled. "
              + "Please use /local/device/activities instead.");
      return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    // 为兼容保留原有行为（返回缓存活动列表）
    List<DeviceActivityDto> activities = sync.getCachedActivities();
    return ResponseEntity.ok(activities);
  }
}
