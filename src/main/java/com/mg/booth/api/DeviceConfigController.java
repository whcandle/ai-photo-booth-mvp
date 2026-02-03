package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.DeviceConfig;
import com.mg.booth.device.DeviceConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Device configuration API Controller
 * Provides read/write interface for device.json
 * Restriction: Only localhost access allowed
 */
@RestController
@RequestMapping("/local/device")
public class DeviceConfigController {

  private static final Logger log = LoggerFactory.getLogger(DeviceConfigController.class);

  private final BoothProps props;
  private final DeviceConfigStore store;

  public DeviceConfigController(
      BoothProps props,
      DeviceConfigStore store) {
    this.props = props;
    this.store = store;
  }

  /**
   * Get device configuration
   * GET /local/device/config
   * 
   * @param request HTTP request (for source check)
   * @return DeviceConfig JSON
   */
  @GetMapping("/config")
  public ResponseEntity<?> getDeviceConfig(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      Path file = Path.of(props.getDeviceIdentityFile());
      DeviceConfig config = store.load(file);
      
      // Log actual file path for debugging
      log.debug("[device-config] Loading device.json from: {}", file.toAbsolutePath());
      
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", config);
      response.put("message", null);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("[device-config] Failed to get device config: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to load device config: " + e.getMessage());
      return ResponseEntity.ok(response);
    }
  }

  /**
   * Save device configuration (only writable fields)
   * PUT /local/device/config
   * 
   * @param config DeviceConfig object (only platformBaseUrl/deviceCode/secret are updated)
   * @param request HTTP request (for source check)
   * @return Save result
   */
  @PutMapping("/config")
  public ResponseEntity<?> saveDeviceConfig(
      @RequestBody DeviceConfig config,
      HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      Path file = Path.of(props.getDeviceIdentityFile());
      DeviceConfig currentConfig = store.load(file);
      
      // Only update writable fields (platformBaseUrl, deviceCode, secret)
      // Preserve read-only fields (deviceId, deviceToken, tokenExpiresAt)
      if (config.getPlatformBaseUrl() != null) {
        currentConfig.setPlatformBaseUrl(config.getPlatformBaseUrl());
      }
      if (config.getDeviceCode() != null) {
        currentConfig.setDeviceCode(config.getDeviceCode());
      }
      if (config.getSecret() != null) {
        currentConfig.setSecret(config.getSecret());
      }
      
      // Save to file
      store.save(file, currentConfig);
      
      log.info("[device-config] Device config saved successfully: platformBaseUrl={}, deviceCode={}", 
          currentConfig.getPlatformBaseUrl(), 
          currentConfig.getDeviceCode() != null && !currentConfig.getDeviceCode().isEmpty() ? "***" : "empty");
      
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", currentConfig);
      response.put("message", "Config saved successfully");
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("[device-config] Failed to save device config: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to save device config: " + e.getMessage());
      return ResponseEntity.ok(response);
    }
  }

  /**
   * Check if request is from localhost
   */
  private boolean isLocalhost(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    String host = request.getHeader("Host");
    
    // Check IP address
    if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
      return true;
    }
    
    // Check Host header (handle reverse proxy cases)
    if (host != null && (host.startsWith("localhost:") || host.startsWith("127.0.0.1:"))) {
      return true;
    }
    
    // Check X-Forwarded-For header (handle reverse proxy)
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null) {
      String firstIp = forwardedFor.split(",")[0].trim();
      if ("127.0.0.1".equals(firstIp) || "localhost".equals(firstIp)) {
        return true;
      }
    }
    
    log.warn("[device-config] Access denied from non-localhost: remoteAddr={}, host={}", remoteAddr, host);
    return false;
  }

  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("message", message);
    return response;
  }
}
