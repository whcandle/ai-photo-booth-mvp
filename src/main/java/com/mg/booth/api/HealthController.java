package com.mg.booth.api;

import com.mg.booth.camera.CameraService;
import com.mg.booth.camera.CameraService.CameraStatus;
import com.mg.booth.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

  private final CameraService cameraService;

  public HealthController(@Qualifier("cameraAgentCameraService") CameraService cameraService) {
    this.cameraService = cameraService;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse(true, "1.0.0", OffsetDateTime.now());
  }

  @GetMapping("/health/camera")
  public Map<String, Object> cameraHealth() {
    Map<String, Object> result = new HashMap<>();
    try {
      CameraStatus status = cameraService.getStatus();
      result.put("ok", status.ok && status.cameraConnected);
      result.put("cameraStatus", status);
      result.put("timestamp", OffsetDateTime.now());
    } catch (Exception e) {
      result.put("ok", false);
      result.put("error", e.getMessage());
      result.put("timestamp", OffsetDateTime.now());
    }
    return result;
  }
}
