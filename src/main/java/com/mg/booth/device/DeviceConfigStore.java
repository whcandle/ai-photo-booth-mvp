package com.mg.booth.device;

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
import java.nio.file.attribute.FileAttribute;

/**
 * device.json read/write utility
 * If file doesn't exist, automatically generates default config
 * Uses atomic write (tmp file + rename) for safety
 */
@Component
public class DeviceConfigStore {
  private static final Logger log = LoggerFactory.getLogger(DeviceConfigStore.class);

  private final ObjectMapper om;

  public DeviceConfigStore() {
    this.om = new ObjectMapper();
    this.om.registerModule(new JavaTimeModule());
    this.om.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Load device.json, if file doesn't exist then generate default config and save
   * 
   * @param file Configuration file path
   * @return DeviceConfig, returns default config if load fails
   */
  public DeviceConfig load(Path file) {
    try {
      if (!Files.exists(file)) {
        log.info("[device-config] device.json not found at {}, creating default config", file.toAbsolutePath());
        DeviceConfig defaultConfig = DeviceConfig.createDefault();
        save(file, defaultConfig);
        return defaultConfig;
      }

      String json = Files.readString(file);
      DeviceConfig config = om.readValue(json, DeviceConfig.class);
      
      // Ensure writable fields are not null
      if (config.getPlatformBaseUrl() == null) {
        config.setPlatformBaseUrl("");
      }
      if (config.getDeviceCode() == null) {
        config.setDeviceCode("");
      }
      if (config.getSecret() == null) {
        config.setSecret("");
      }

      log.debug("[device-config] Loaded config from {}", file.toAbsolutePath());
      return config;
    } catch (Exception e) {
      log.error("[device-config] Failed to load device config from {}: {}", 
          file.toAbsolutePath(), e.getMessage(), e);
      // Return default config, don't throw exception
      log.warn("[device-config] Returning default config due to load error");
      return DeviceConfig.createDefault();
    }
  }

  /**
   * Save device.json using atomic write (tmp file + rename)
   * 
   * @param file Configuration file path
   * @param config Configuration object
   */
  public void save(Path file, DeviceConfig config) {
    Path tmpFile = null;
    try {
      String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(config);
      
      // Ensure parent directory exists
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      
      // Atomic write: write to tmp file first, then rename
      tmpFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
      Files.writeString(tmpFile, json, 
          StandardOpenOption.CREATE, 
          StandardOpenOption.TRUNCATE_EXISTING, 
          StandardOpenOption.WRITE);
      
      // Try atomic move first, fallback to replace if not supported
      try {
        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (UnsupportedOperationException e) {
        // ATOMIC_MOVE not supported on this filesystem, use REPLACE_EXISTING
        log.debug("[device-config] ATOMIC_MOVE not supported, using REPLACE_EXISTING");
        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
      }
      
      log.info("[device-config] device.json saved to {} (atomic write)", file.toAbsolutePath());
    } catch (Exception e) {
      log.error("[device-config] Failed to save device config to {}: {}", 
          file.toAbsolutePath(), e.getMessage(), e);
      // Clean up tmp file if exists
      if (tmpFile != null) {
        try {
          Files.deleteIfExists(tmpFile);
        } catch (Exception ex) {
          log.warn("[device-config] Failed to delete tmp file: {}", ex.getMessage());
        }
      }
      throw new RuntimeException("Failed to save device config", e);
    }
  }

  /**
   * Get configuration file path (for logging, etc.)
   */
  public Path getConfigPath(String configFile) {
    return Path.of(configFile);
  }
}
