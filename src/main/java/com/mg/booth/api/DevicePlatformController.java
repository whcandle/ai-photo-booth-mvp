package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.DeviceCacheStore;
import com.mg.booth.device.DeviceConfig;
import com.mg.booth.device.DeviceConfigStore;
import com.mg.booth.device.PlatformCallException;
import com.mg.booth.device.PlatformDeviceApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Device Platform Proxy Controller
 * Provides localhost-only proxy interfaces for platform API calls
 * Base path: /local/device
 */
@RestController
@RequestMapping("/local/device")
public class DevicePlatformController {

  private static final Logger log = LoggerFactory.getLogger(DevicePlatformController.class);

  private final BoothProps props;
  private final DeviceConfigStore configStore;
  private final DeviceCacheStore cacheStore;
  private final PlatformDeviceApiClient apiClient;

  public DevicePlatformController(
      BoothProps props,
      DeviceConfigStore configStore,
      DeviceCacheStore cacheStore,
      @Qualifier("devicePlatformDeviceApiClient") PlatformDeviceApiClient apiClient) {
    this.props = props;
    this.configStore = configStore;
    this.cacheStore = cacheStore;
    this.apiClient = apiClient;
  }

  /**
   * POST /local/device/handshake
   * Execute handshake with platform and update device.json
   * 
   * @param request HTTP request (for localhost check)
   * @return Handshake result with updated DeviceConfig
   */
  @PostMapping("/handshake")
  public ResponseEntity<?> handshake(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      // 1. Load device.json
      Path file = Path.of(props.getDeviceIdentityFile());
      DeviceConfig config = configStore.load(file);

      // 2. Validate required fields
      if (config.getPlatformBaseUrl() == null || config.getPlatformBaseUrl().isBlank()) {
        return ResponseEntity.ok(createErrorResponse("platformBaseUrl not configured"));
      }
      if (config.getDeviceCode() == null || config.getDeviceCode().isBlank()) {
        return ResponseEntity.ok(createErrorResponse("deviceCode not configured"));
      }
      if (config.getSecret() == null || config.getSecret().isBlank()) {
        return ResponseEntity.ok(createErrorResponse("secret not configured"));
      }

      // 3. Call platform handshake
      var handshakeData = apiClient.handshake(
          config.getPlatformBaseUrl(),
          config.getDeviceCode(),
          config.getSecret()
      );

      // 4. Update config with handshake result
      config.setDeviceIdFromLong(handshakeData.deviceId());
      config.setDeviceToken(handshakeData.deviceToken());
      config.setTokenExpiresAtFromInstant(handshakeData.tokenExpiresAt());

      // 5. Save to device.json (atomic write)
      configStore.save(file, config);

      log.info("[device-platform] Handshake successful: deviceId={}, tokenExpiresAt={}", 
          config.getDeviceId(), config.getTokenExpiresAt());

      // 6. Return success response
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", config);
      response.put("message", "OK");
      return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
      log.error("[device-platform] Handshake failed: {}", e.getMessage());
      return ResponseEntity.ok(createErrorResponse(e.getMessage()));
    } catch (PlatformCallException e) {
      log.error("[device-platform] Handshake failed: url={}, status={}, reason={}", 
          e.getUrl(), e.getHttpStatus(), e.getReason());
      return ResponseEntity.ok(createErrorResponse(
          String.format("Handshake failed: %s", e.getMessage())));
    } catch (Exception e) {
      log.error("[device-platform] Handshake failed: {}", e.getMessage(), e);
      return ResponseEntity.ok(createErrorResponse("Handshake failed: " + e.getMessage()));
    }
  }

  /**
   * GET /local/device/activities
   * Get activities list from platform (online first, fallback to cache)
   * 
   * @param request HTTP request (for localhost check)
   * @return Activities list with stale flag
   */
  @GetMapping("/activities")
  public ResponseEntity<?> getActivities(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      // 1. Load device.json
      Path file = Path.of(props.getDeviceIdentityFile());
      DeviceConfig config = configStore.load(file);
      Path dir = file.getParent() != null ? file.getParent() : Path.of(".");

      // 2. Validate required fields
      if (config.getPlatformBaseUrl() == null || config.getPlatformBaseUrl().isBlank()) {
        return ResponseEntity.ok(createErrorResponse("platformBaseUrl not configured"));
      }
      if (config.getDeviceId() == null || config.getDeviceId().isBlank()) {
        return ResponseEntity.ok(createErrorResponse("deviceId not configured (handshake required)"));
      }
      if (config.getDeviceToken() == null || config.getDeviceToken().isBlank()) {
        return ResponseEntity.ok(createErrorResponse("deviceToken not configured (handshake required)"));
      }

      Long deviceId = config.getDeviceIdAsLong();
      if (deviceId == null) {
        return ResponseEntity.ok(createErrorResponse("invalid deviceId"));
      }

      // 3. Try online call first
      try {
        List<Map<String, Object>> activities = apiClient.listActivities(
            config.getPlatformBaseUrl(),
            deviceId,
            config.getDeviceToken()
        );

        // 4. Online call successful: write cache and return
        cacheStore.writeActivitiesCache(dir, activities);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("items", activities);
        data.put("stale", false);
        response.put("data", data);
        response.put("message", null);

        log.info("[device-platform] Activities fetched successfully: count={}", activities.size());
        return ResponseEntity.ok(response);

      } catch (PlatformCallException e) {
        // Handle platform call exceptions
        if (e.isUnauthorized()) {
          // 401: Token invalid/expired
          log.warn("[device-platform] Activities failed: 401 unauthorized");
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(createErrorResponse("token invalid/expired"));
        } else if (e.isUnreachable()) {
          // 503: Platform unreachable, try cache
          log.warn("[device-platform] Activities failed: platform unreachable (status={}, reason={}), trying cache", 
              e.getHttpStatus(), e.getReason());
          
          var cacheOpt = cacheStore.readActivitiesCache(dir);
          if (cacheOpt.isPresent()) {
            // Cache exists: return cached data with stale=true
            var cache = cacheOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("items", cache.getItems());
            data.put("stale", true);
            data.put("cachedAt", cache.getCachedAt().toString());
            response.put("data", data);
            response.put("message", "using cached data");

            log.info("[device-platform] Using cached activities: count={}, cachedAt={}", 
                cache.getItems().size(), cache.getCachedAt());
            return ResponseEntity.ok(response);
          } else {
            // No cache: return 503
            log.error("[device-platform] Platform unreachable and no cache available");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("platform unreachable and no cache"));
          }
        } else {
          // Other HTTP errors: return error
          log.error("[device-platform] Activities failed: status={}, reason={}", 
              e.getHttpStatus(), e.getReason());
          HttpStatus status = e.getHttpStatus() > 0 
              ? HttpStatus.valueOf(e.getHttpStatus()) 
              : HttpStatus.INTERNAL_SERVER_ERROR;
          return ResponseEntity.status(status)
              .body(createErrorResponse(String.format("Activities failed: %s", e.getMessage())));
        }
      }

    } catch (Exception e) {
      log.error("[device-platform] Get activities failed: {}", e.getMessage(), e);
      return ResponseEntity.ok(createErrorResponse("Failed to get activities: " + e.getMessage()));
    }
  }

  /**
   * Check if request is from localhost (simplified: only trust remoteAddr)
   */
  private boolean isLocalhost(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    
    // Only trust remoteAddr (127.0.0.1, ::1, or 0:0:0:0:0:0:0:1)
    if ("127.0.0.1".equals(remoteAddr) 
        || "0:0:0:0:0:0:0:1".equals(remoteAddr) 
        || "::1".equals(remoteAddr)) {
      return true;
    }
    
    log.warn("[device-platform] Access denied from non-localhost: remoteAddr={}", remoteAddr);
    return false;
  }

  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("data", null);
    response.put("message", message);
    return response;
  }
}
