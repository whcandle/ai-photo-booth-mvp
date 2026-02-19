package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.LocalTemplateIndexStore;
import com.mg.booth.device.TemplatePackageInstaller;
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
 * Template installation controller
 * Provides localhost-only interfaces for template package installation
 */
@RestController
@RequestMapping("/local/device/templates")
public class TemplateController {

  private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

  private final BoothProps props;
  private final TemplatePackageInstaller installer;
  private final LocalTemplateIndexStore indexStore;

  public TemplateController(
      BoothProps props,
      TemplatePackageInstaller installer,
      LocalTemplateIndexStore indexStore) {
    this.props = props;
    this.installer = installer;
    this.indexStore = indexStore;
  }

  /**
   * POST /local/device/templates/install
   * Install template package
   * 
   * Request body (new format, preferred):
   *   - templateCode: String (e.g. "tpl_001")
   *   - versionSemver: String (e.g. "0.1.0")
   *   - downloadUrl: String
   *   - checksumSha256: String
   * 
   * Request body (legacy format, deprecated):
   *   - templateId: String (deprecated, use templateCode instead)
   *   - version: String (deprecated, use versionSemver instead)
   *   - downloadUrl: String
   *   - checksum: String (deprecated, use checksumSha256 instead)
   * 
   * @param request HTTP request (for localhost check)
   * @param body Request body containing templateCode/versionSemver (preferred) or templateId/version (legacy)
   * @return Installation result
   */
  @PostMapping("/install")
  public ResponseEntity<?> install(
      HttpServletRequest request,
      @RequestBody Map<String, Object> body) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      // Extract parameters: prefer new fields (templateCode/versionSemver), fallback to legacy (templateId/version)
      String templateCode = extractString(body, "templateCode");
      String versionSemver = extractString(body, "versionSemver");
      String downloadUrl = extractString(body, "downloadUrl");
      String checksumSha256 = extractString(body, "checksumSha256");
      
      // Legacy field support (deprecated)
      if (templateCode == null || templateCode.isBlank()) {
        String templateId = extractString(body, "templateId");
        if (templateId != null && !templateId.isBlank()) {
          log.warn("[template-controller] Using deprecated field 'templateId', please migrate to 'templateCode'");
          templateCode = templateId;
        }
      }
      
      if (versionSemver == null || versionSemver.isBlank()) {
        String version = extractString(body, "version");
        if (version != null && !version.isBlank()) {
          log.warn("[template-controller] Using deprecated field 'version', please migrate to 'versionSemver'");
          versionSemver = version;
        }
      }
      
      if (checksumSha256 == null || checksumSha256.isBlank()) {
        String checksum = extractString(body, "checksum");
        if (checksum != null && !checksum.isBlank()) {
          log.warn("[template-controller] Using deprecated field 'checksum', please migrate to 'checksumSha256'");
          checksumSha256 = checksum;
        }
      }

      // Validate required fields
      if (templateCode == null || templateCode.isBlank()) {
        return ResponseEntity.ok(createErrorResponse(
            "templateCode is required (or use deprecated templateId field)"));
      }
      if (versionSemver == null || versionSemver.isBlank()) {
        return ResponseEntity.ok(createErrorResponse(
            "versionSemver is required (or use deprecated version field)"));
      }
      if (downloadUrl == null || downloadUrl.isBlank()) {
        return ResponseEntity.ok(createErrorResponse("downloadUrl is required"));
      }
      if (checksumSha256 == null || checksumSha256.isBlank()) {
        return ResponseEntity.ok(createErrorResponse(
            "checksumSha256 is required (or use deprecated checksum field)"));
      }

      log.info("[template-controller] Install request: templateCode={}, versionSemver={}, downloadUrl={}", 
          templateCode, versionSemver, downloadUrl);

      // Install template package
      String installedPath = installer.install(templateCode, versionSemver, downloadUrl, checksumSha256);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      Map<String, Object> data = new HashMap<>();
      data.put("installedPath", installedPath);
      data.put("indexUpdated", true);
      response.put("data", data);
      response.put("message", null);

      log.info("[template-controller] Installation successful: templateCode={}, versionSemver={}, path={}", 
          templateCode, versionSemver, installedPath);

      return ResponseEntity.ok(response);

    } catch (RuntimeException e) {
      log.error("[template-controller] Installation failed: {}", e.getMessage(), e);
      return ResponseEntity.ok(createErrorResponse("Installation failed: " + e.getMessage()));
    } catch (Exception e) {
      log.error("[template-controller] Installation failed: {}", e.getMessage(), e);
      return ResponseEntity.ok(createErrorResponse("Installation failed: " + e.getMessage()));
    }
  }

  /**
   * GET /local/device/templates/installed
   * Get installed templates index
   * 
   * @param request HTTP request (for localhost check)
   * @return Template index
   */
  @GetMapping("/installed")
  public ResponseEntity<?> getInstalled(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      Path dataDir = Path.of(props.getDataDir() != null && !props.getDataDir().isBlank() 
          ? props.getDataDir() 
          : "./data");
      Path indexFile = dataDir.resolve("index.json");

      var indexOpt = indexStore.readIndex(indexFile);
      if (indexOpt.isEmpty()) {
        // Return empty index
        LocalTemplateIndexStore.TemplateIndex emptyIndex = new LocalTemplateIndexStore.TemplateIndex();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", emptyIndex);
        response.put("message", null);
        return ResponseEntity.ok(response);
      }

      LocalTemplateIndexStore.TemplateIndex index = indexOpt.get();
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", index);
      response.put("message", null);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("[template-controller] Failed to get installed templates: {}", e.getMessage(), e);
      return ResponseEntity.ok(createErrorResponse("Failed to get installed templates: " + e.getMessage()));
    }
  }

  /**
   * Check if request is from localhost
   */
  private boolean isLocalhost(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    
    if ("127.0.0.1".equals(remoteAddr) 
        || "0:0:0:0:0:0:0:1".equals(remoteAddr) 
        || "::1".equals(remoteAddr)) {
      return true;
    }
    
    log.warn("[template-controller] Access denied from non-localhost: remoteAddr={}", remoteAddr);
    return false;
  }

  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("data", null);
    response.put("message", message);
    return response;
  }

  private String extractString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }
}
