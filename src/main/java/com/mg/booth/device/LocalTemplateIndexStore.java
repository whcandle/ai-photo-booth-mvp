package com.mg.booth.device;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Local template index store
 * Manages index.json for installed templates (atomic write)
 */
@Component
public class LocalTemplateIndexStore {
  private static final Logger log = LoggerFactory.getLogger(LocalTemplateIndexStore.class);

  private final ObjectMapper om;

  public LocalTemplateIndexStore() {
    this.om = new ObjectMapper();
    this.om.registerModule(new JavaTimeModule());
    this.om.enable(SerializationFeature.INDENT_OUTPUT);
    this.om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Read template index from file
   * Supports migration from schemaVersion 1 to 2 (templateId is now templateCode)
   * 
   * @param indexFile Path to index.json
   * @return Optional TemplateIndex, empty if file doesn't exist or parse fails
   */
  public Optional<TemplateIndex> readIndex(Path indexFile) {
    try {
      if (!Files.exists(indexFile)) {
        log.debug("[template-index] Index file not found at {}, will create on first write", 
            indexFile.toAbsolutePath());
        return Optional.empty();
      }

      String json = Files.readString(indexFile);
      TemplateIndex index = om.readValue(json, TemplateIndex.class);
      
      // Migrate from schemaVersion 1 to 2 if needed
      if (index.getSchemaVersion() == null || index.getSchemaVersion() == 1) {
        log.info("[template-index] Migrating index from schemaVersion 1 to 2");
        index.setSchemaVersion(2);
        // Note: templateId field is already String type, so no data migration needed
        // The field now represents templateCode instead of numeric ID
      }
      
      log.debug("[template-index] Index loaded from {}, schemaVersion={}, items={}", 
          indexFile.toAbsolutePath(), 
          index.getSchemaVersion(),
          index.getItems() != null ? index.getItems().size() : 0);
      
      return Optional.of(index);
    } catch (Exception e) {
      log.warn("[template-index] Failed to load index from {}: {}. Will create new index.", 
          indexFile.toAbsolutePath(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Write template index to file (atomic write)
   * 
   * @param indexFile Path to index.json
   * @param index TemplateIndex to write
   */
  public void writeIndex(Path indexFile, TemplateIndex index) {
    Path tmpFile = null;
    
    try {
      // Update updatedAt timestamp
      index.setUpdatedAt(Instant.now());
      
      String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(index);
      
      // Ensure parent directory exists
      Path parent = indexFile.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      
      // Atomic write: write to tmp file first, then rename
      tmpFile = indexFile.resolveSibling(indexFile.getFileName().toString() + ".tmp");
      Files.writeString(tmpFile, json, 
          StandardOpenOption.CREATE, 
          StandardOpenOption.TRUNCATE_EXISTING, 
          StandardOpenOption.WRITE);
      
      // Try atomic move first, fallback to replace if not supported
      try {
        Files.move(tmpFile, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (UnsupportedOperationException e) {
        log.debug("[template-index] ATOMIC_MOVE not supported, using REPLACE_EXISTING");
        Files.move(tmpFile, indexFile, StandardCopyOption.REPLACE_EXISTING);
      }
      
      log.info("[template-index] Index saved to {} (atomic write), items={}", 
          indexFile.toAbsolutePath(), 
          index.getItems() != null ? index.getItems().size() : 0);
    } catch (Exception e) {
      log.error("[template-index] Failed to save index to {}: {}", 
          indexFile.toAbsolutePath(), e.getMessage(), e);
      // Clean up tmp file if exists
      if (tmpFile != null) {
        try {
          Files.deleteIfExists(tmpFile);
        } catch (Exception ex) {
          log.warn("[template-index] Failed to delete tmp file: {}", ex.getMessage());
        }
      }
      throw new RuntimeException("Failed to save template index", e);
    }
  }

  /**
   * Template index structure
   * schemaVersion 2: templateId field now stores templateCode (String) instead of numeric ID
   */
  public static class TemplateIndex {
    private Integer schemaVersion = 2;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;
    
    private List<TemplateIndexItem> items = new ArrayList<>();

    public TemplateIndex() {
      // Default constructor for Jackson
    }

    public Integer getSchemaVersion() {
      return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
      this.schemaVersion = schemaVersion;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
    }

    public List<TemplateIndexItem> getItems() {
      return items;
    }

    public void setItems(List<TemplateIndexItem> items) {
      this.items = items != null ? items : new ArrayList<>();
    }
  }

  /**
   * Template index item
   * schemaVersion 2: templateId field stores templateCode (String, e.g. "tpl_001")
   *                  version field stores versionSemver (String, e.g. "0.1.0")
   */
  public static class TemplateIndexItem {
    private String templateId;  // Stores templateCode (String) in schemaVersion 2
    private String version;     // Stores versionSemver (String) in schemaVersion 2
    private String path;        // Relative path from dataDir, e.g., "templates/tpl_001/0.1.0"
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant installedAt;
    
    private String checksum;  // sha256 of zip file
    private String downloadUrl;

    public TemplateIndexItem() {
      // Default constructor for Jackson
    }

    public TemplateIndexItem(String templateId, String version, String path, 
                            Instant installedAt, String checksum, String downloadUrl) {
      this.templateId = templateId;
      this.version = version;
      this.path = path;
      this.installedAt = installedAt;
      this.checksum = checksum;
      this.downloadUrl = downloadUrl;
    }

    public String getTemplateId() {
      return templateId;
    }

    public void setTemplateId(String templateId) {
      this.templateId = templateId;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public Instant getInstalledAt() {
      return installedAt;
    }

    public void setInstalledAt(Instant installedAt) {
      this.installedAt = installedAt;
    }

    public String getChecksum() {
      return checksum;
    }

    public void setChecksum(String checksum) {
      this.checksum = checksum;
    }

    public String getDownloadUrl() {
      return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
      this.downloadUrl = downloadUrl;
    }
  }
}
