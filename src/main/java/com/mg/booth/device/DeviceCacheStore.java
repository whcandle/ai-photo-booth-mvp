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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Device cache store for activities and other cached data
 * Uses atomic write (tmp file + rename) for safety
 */
@Component
public class DeviceCacheStore {
  private static final Logger log = LoggerFactory.getLogger(DeviceCacheStore.class);

  private final ObjectMapper om;

  public DeviceCacheStore() {
    this.om = new ObjectMapper();
    this.om.registerModule(new JavaTimeModule());
    this.om.enable(SerializationFeature.INDENT_OUTPUT);
    // Configure Instant to serialize as ISO8601 string
    this.om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Write activities cache to file (atomic write)
   * 
   * @param dir Directory where device.json is located
   * @param items Activities list
   */
  public void writeActivitiesCache(Path dir, List<Map<String, Object>> items) {
    Path cacheFile = dir.resolve("activities_cache.json");
    Path tmpFile = null;
    
    try {
      CachePayload payload = new CachePayload(Instant.now(), items);
      String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
      
      // Ensure parent directory exists
      if (!Files.exists(dir)) {
        Files.createDirectories(dir);
      }
      
      // Atomic write: write to tmp file first, then rename
      tmpFile = cacheFile.resolveSibling(cacheFile.getFileName().toString() + ".tmp");
      Files.writeString(tmpFile, json, 
          StandardOpenOption.CREATE, 
          StandardOpenOption.TRUNCATE_EXISTING, 
          StandardOpenOption.WRITE);
      
      // Try atomic move first, fallback to replace if not supported
      try {
        Files.move(tmpFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (UnsupportedOperationException e) {
        log.debug("[cache] ATOMIC_MOVE not supported, using REPLACE_EXISTING");
        Files.move(tmpFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
      }
      
      log.info("[cache] Activities cache saved to {} (atomic write), items={}", 
          cacheFile.toAbsolutePath(), items.size());
    } catch (Exception e) {
      log.error("[cache] Failed to save activities cache to {}: {}", 
          cacheFile.toAbsolutePath(), e.getMessage(), e);
      // Clean up tmp file if exists
      if (tmpFile != null) {
        try {
          Files.deleteIfExists(tmpFile);
        } catch (Exception ex) {
          log.warn("[cache] Failed to delete tmp file: {}", ex.getMessage());
        }
      }
      throw new RuntimeException("Failed to save activities cache", e);
    }
  }

  /**
   * Read activities cache from file
   * 
   * @param dir Directory where device.json is located
   * @return Optional CachePayload, empty if file doesn't exist or parse fails
   */
  public Optional<CachePayload> readActivitiesCache(Path dir) {
    Path cacheFile = dir.resolve("activities_cache.json");
    
    try {
      if (!Files.exists(cacheFile)) {
        log.debug("[cache] Activities cache not found at {}", cacheFile.toAbsolutePath());
        return Optional.empty();
      }

      String json = Files.readString(cacheFile);
      CachePayload payload = om.readValue(json, CachePayload.class);
      
      log.debug("[cache] Activities cache loaded from {}, items={}, cachedAt={}", 
          cacheFile.toAbsolutePath(), 
          payload.getItems() != null ? payload.getItems().size() : 0,
          payload.getCachedAt());
      
      return Optional.of(payload);
    } catch (Exception e) {
      log.warn("[cache] Failed to load activities cache from {}: {}. Cache will be ignored.", 
          cacheFile.toAbsolutePath(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Cache payload containing cached data and timestamp
   */
  public static class CachePayload {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant cachedAt;
    private List<Map<String, Object>> items;

    public CachePayload() {
      // Default constructor for Jackson
    }

    public CachePayload(Instant cachedAt, List<Map<String, Object>> items) {
      this.cachedAt = cachedAt;
      this.items = items;
    }

    public Instant getCachedAt() {
      return cachedAt;
    }

    public void setCachedAt(Instant cachedAt) {
      this.cachedAt = cachedAt;
    }

    public List<Map<String, Object>> getItems() {
      return items;
    }

    public void setItems(List<Map<String, Object>> items) {
      this.items = items;
    }
  }
}
