package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.LocalTemplateIndexStore;
import com.mg.booth.domain.TemplateSummary;
import com.mg.booth.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Templates API Controller
 * Provides GET /api/v1/templates endpoint for kiosk Selecting page
 * 
 * Data source priority:
 * 1. LocalTemplateIndexStore (data/index.json) - preferred
 * 2. TemplateService.listTemplates() - fallback if index is empty
 */
@RestController
@RequestMapping("/api/v1")
public class TemplatesApiController {

  private static final Logger log = LoggerFactory.getLogger(TemplatesApiController.class);

  private final BoothProps props;
  private final LocalTemplateIndexStore indexStore;
  private final TemplateService templateService;

  public TemplatesApiController(
      BoothProps props,
      LocalTemplateIndexStore indexStore,
      TemplateService templateService) {
    this.props = props;
    this.indexStore = indexStore;
    this.templateService = templateService;
  }

  /**
   * GET /api/v1/templates
   * Get list of available templates for kiosk Selecting page
   * 
   * Returns fields: templateId, name, enabled, previewImageUrl
   * previewImageUrl is set only when versionSemver is available (from LocalTemplateIndexStore)
   * Does not expose downloadUrl/checksum/version (those are for Settings page installation)
   * 
   * @return Response with items array: { "items": [...] }
   */
  @GetMapping("/templates")
  public Map<String, Object> listTemplates() {
    List<TemplateItemDto> items = new ArrayList<>();
    String source = "unknown";

    try {
      // Step 1: Try to read from LocalTemplateIndexStore (data/index.json)
      Path dataDir = Path.of(props.getDataDir() != null && !props.getDataDir().isBlank() 
          ? props.getDataDir() 
          : "./data");
      Path indexFile = dataDir.resolve("index.json");

      var indexOpt = indexStore.readIndex(indexFile);
      
      if (indexOpt.isPresent() && indexOpt.get().getItems() != null 
          && !indexOpt.get().getItems().isEmpty()) {
        // Use local index
        source = "local_index";
        LocalTemplateIndexStore.TemplateIndex index = indexOpt.get();
        
        for (LocalTemplateIndexStore.TemplateIndexItem item : index.getItems()) {
          TemplateItemDto dto = new TemplateItemDto();
          // templateId stores templateCode in schemaVersion 2
          String templateId = item.getTemplateId() != null ? item.getTemplateId() : "unknown";
          dto.setTemplateId(templateId);
          // Use templateId as name if no name field available
          dto.setName(templateId);
          // Local installed templates are always enabled
          dto.setEnabled(true);
          
          // Set previewImageUrl and versionSemver if version is available
          String versionSemver = item.getVersion();
          if (versionSemver != null && !versionSemver.isBlank()) {
            String previewUrl = "/api/v1/templates/" + templateId + "/" + versionSemver + "/preview";
            dto.setPreviewImageUrl(previewUrl);
            dto.setVersionSemver(versionSemver);
            log.debug("[templates-api] templateId={} version={} previewImageUrl={}", 
                templateId, versionSemver, previewUrl);
          } else {
            dto.setPreviewImageUrl(null);
            dto.setVersionSemver(null);
            log.debug("[templates-api] templateId={} version=null previewImageUrl=null", templateId);
          }
          
          items.add(dto);
        }
      } else {
        // Step 2: Fallback to TemplateService (hardcoded templates)
        source = "fallback_service";
        List<TemplateSummary> summaries = templateService.listTemplates();
        
        for (TemplateSummary summary : summaries) {
          TemplateItemDto dto = new TemplateItemDto();
          String templateId = summary.getTemplateId();
          dto.setTemplateId(templateId);
          dto.setName(summary.getName());
          dto.setEnabled(summary.isEnabled());
          
          // TemplateService (hardcoded) doesn't have version, so previewImageUrl is null
          dto.setPreviewImageUrl(null);
          dto.setVersionSemver(null);
          log.debug("[templates-api] templateId={} version=null previewImageUrl=null (fallback service)", 
              templateId);
          
          items.add(dto);
        }
      }

      log.info("[templates-api] List templates source={} count={}", source, items.size());

      Map<String, Object> response = new HashMap<>();
      response.put("items", items);
      return response;

    } catch (Exception e) {
      log.error("[templates-api] Failed to list templates: {}", e.getMessage(), e);
      
      // Fail-open: fallback to TemplateService even on error
      try {
        source = "fallback_service_error";
        List<TemplateSummary> summaries = templateService.listTemplates();
        items.clear();
        for (TemplateSummary summary : summaries) {
          TemplateItemDto dto = new TemplateItemDto();
          String templateId = summary.getTemplateId();
          dto.setTemplateId(templateId);
          dto.setName(summary.getName());
          dto.setEnabled(summary.isEnabled());
          
          // TemplateService (hardcoded) doesn't have version, so previewImageUrl is null
          dto.setPreviewImageUrl(null);
          dto.setVersionSemver(null);
          log.debug("[templates-api] templateId={} version=null previewImageUrl=null (fallback after error)", 
              templateId);
          
          items.add(dto);
        }
        log.info("[templates-api] List templates source={} count={} (fallback after error)", 
            source, items.size());
      } catch (Exception fallbackError) {
        log.error("[templates-api] Fallback also failed: {}", fallbackError.getMessage(), fallbackError);
        // Return empty list if everything fails
        items = new ArrayList<>();
      }

      Map<String, Object> response = new HashMap<>();
      response.put("items", items);
      return response;
    }
  }

  /**
   * Template item DTO for API response
   * Fields: templateId, name, enabled, previewImageUrl, versionSemver
   */
  public static class TemplateItemDto {
    private String templateId;
    private String name;
    private boolean enabled;
    private String previewImageUrl;
    private String versionSemver;

    public TemplateItemDto() {
      // Default constructor
    }

    public String getTemplateId() {
      return templateId;
    }

    public void setTemplateId(String templateId) {
      this.templateId = templateId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getPreviewImageUrl() {
      return previewImageUrl;
    }

    public void setPreviewImageUrl(String previewImageUrl) {
      this.previewImageUrl = previewImageUrl;
    }

    public String getVersionSemver() {
      return versionSemver;
    }

    public void setVersionSemver(String versionSemver) {
      this.versionSemver = versionSemver;
    }
  }
}
