package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Template Preview Controller
 * Provides GET /api/v1/templates/{templateId}/{versionSemver}/preview endpoint
 * for serving template preview images.
 */
@RestController
@RequestMapping("/api/v1")
public class TemplatePreviewController {

  private static final Logger log = LoggerFactory.getLogger(TemplatePreviewController.class);

  private final BoothProps boothProps;

  public TemplatePreviewController(BoothProps boothProps) {
    this.boothProps = boothProps;
  }

  /**
   * GET /api/v1/templates/{templateId}/{versionSemver}/preview
   * Get template preview image
   * 
   * @param templateId Template ID (e.g., "tpl_002")
   * @param versionSemver Version semver (e.g., "0.1.2")
   * @return Preview image bytes or 404 if not found
   */
  @GetMapping("/templates/{templateId}/{versionSemver}/preview")
  public ResponseEntity<?> getPreview(
      @PathVariable String templateId,
      @PathVariable String versionSemver) {
    
    // Get data directory from config
    String dataDir = boothProps.getDataDir();
    if (dataDir == null || dataDir.isBlank()) {
      dataDir = "./data";
    }

    // Build preview image path: {dataDir}/templates/{templateId}/{versionSemver}/preview.jpg
    Path previewJpg = Paths.get(dataDir, "templates", templateId, versionSemver, "preview.jpg");
    Path previewPng = Paths.get(dataDir, "templates", templateId, versionSemver, "preview.png");
    
    Path previewPath = null;
    MediaType contentType = null;
    boolean exists = false;

    // Try preview.jpg first, then preview.png
    if (Files.exists(previewJpg) && Files.isRegularFile(previewJpg)) {
      previewPath = previewJpg;
      contentType = MediaType.IMAGE_JPEG;
      exists = true;
    } else if (Files.exists(previewPng) && Files.isRegularFile(previewPng)) {
      previewPath = previewPng;
      contentType = MediaType.IMAGE_PNG;
      exists = true;
    }

    // Log the request
    log.info("[template-preview] templateId={} version={} path={} exists={}",
        templateId, versionSemver, 
        previewPath != null ? previewPath.toAbsolutePath() : "none", 
        exists);

    if (!exists || previewPath == null) {
      // Return 404 with JSON response
      Map<String, Object> error = new HashMap<>();
      error.put("error", "NOT_FOUND");
      error.put("message", "Template preview not found");
      error.put("templateId", templateId);
      error.put("version", versionSemver);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_JSON)
          .body(error);
    }

    try {
      // Read file bytes
      byte[] imageBytes = Files.readAllBytes(previewPath);

      // Build response headers
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(contentType);
      headers.setCacheControl(CacheControl.maxAge(3600, java.util.concurrent.TimeUnit.SECONDS).cachePublic());

      return ResponseEntity.ok()
          .headers(headers)
          .body(imageBytes);

    } catch (Exception e) {
      log.error("[template-preview] Failed to read preview file: templateId={}, version={}, path={}, error={}",
          templateId, versionSemver, previewPath.toAbsolutePath(), e.getMessage(), e);
      
      Map<String, Object> error = new HashMap<>();
      error.put("error", "INTERNAL_ERROR");
      error.put("message", "Failed to read preview file: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body(error);
    }
  }
}
