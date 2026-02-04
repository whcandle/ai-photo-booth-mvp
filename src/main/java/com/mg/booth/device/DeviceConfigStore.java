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
import java.time.Instant;
import java.util.Map;

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
      
      // First try to parse as Map to handle compatibility (deviceId might be Long in old format)
      @SuppressWarnings("unchecked")
      Map<String, Object> rawMap = om.readValue(json, Map.class);
      
      DeviceConfig config = new DeviceConfig();
      
      // Set writable fields
      config.setPlatformBaseUrl(extractString(rawMap, "platformBaseUrl"));
      config.setDeviceCode(extractString(rawMap, "deviceCode"));
      config.setSecret(extractString(rawMap, "secret"));
      
      // Set read-only fields with compatibility handling
      Object deviceIdObj = rawMap.get("deviceId");
      if (deviceIdObj != null) {
        // Handle both String and Long (backward compatibility)
        if (deviceIdObj instanceof Long) {
          config.setDeviceIdFromLong((Long) deviceIdObj);
        } else if (deviceIdObj instanceof Number) {
          config.setDeviceIdFromLong(((Number) deviceIdObj).longValue());
        } else {
          config.setDeviceId(String.valueOf(deviceIdObj));
        }
      }
      
      config.setDeviceToken(extractString(rawMap, "deviceToken"));
      
      // Handle tokenExpiresAt with compatibility (might be Instant serialized as number or ISO8601 string)
      Object tokenExpiresAtObj = rawMap.get("tokenExpiresAt");
      if (tokenExpiresAtObj != null) {
        if (tokenExpiresAtObj instanceof String) {
          String tokenExpiresAtStr = (String) tokenExpiresAtObj;
          // Check if it's a valid ISO8601 string
          try {
            Instant.parse(tokenExpiresAtStr);
            // Valid ISO8601, use it directly
            config.setTokenExpiresAt(tokenExpiresAtStr);
          } catch (Exception e) {
            // Not valid ISO8601, might be a number string (backward compatibility)
            try {
              double timestamp = Double.parseDouble(tokenExpiresAtStr);
              // Convert to Instant: treat as seconds with fractional part
              long seconds = (long) timestamp;
              long nanos = (long) ((timestamp - seconds) * 1e9);
              Instant instant = Instant.ofEpochSecond(seconds, nanos);
              config.setTokenExpiresAtFromInstant(instant);
              log.info("[device-config] Converted tokenExpiresAt from number string {} to ISO8601: {}", 
                  tokenExpiresAtStr, config.getTokenExpiresAt());
            } catch (Exception ex) {
              // Not a number either, keep as is
              log.warn("[device-config] tokenExpiresAt is neither ISO8601 nor number: {}, keeping as is", tokenExpiresAtStr);
              config.setTokenExpiresAt(tokenExpiresAtStr);
            }
          }
        } else if (tokenExpiresAtObj instanceof Number) {
          // Might be Unix timestamp (seconds or nanoseconds)
          // Try to convert to Instant then to ISO8601
          try {
            double timestamp = ((Number) tokenExpiresAtObj).doubleValue();
            // Convert to Instant: treat as seconds with fractional part
            // e.g., 1770206725.580518100 = 1770206725 seconds + 0.580518100 seconds
            long seconds = (long) timestamp;
            long nanos = (long) ((timestamp - seconds) * 1e9);
            Instant instant = Instant.ofEpochSecond(seconds, nanos);
            config.setTokenExpiresAtFromInstant(instant);
            log.info("[device-config] Converted tokenExpiresAt from number {} to ISO8601: {}", 
                tokenExpiresAtObj, config.getTokenExpiresAt());
          } catch (Exception e) {
            log.warn("[device-config] Failed to parse tokenExpiresAt as timestamp: {}, using as string", tokenExpiresAtObj);
            config.setTokenExpiresAt(String.valueOf(tokenExpiresAtObj));
          }
        } else {
          config.setTokenExpiresAt(String.valueOf(tokenExpiresAtObj));
        }
      }
      
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
      // Normalize config before saving to ensure correct format
      normalizeConfig(config);
      
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

  /**
   * Extract String value from map, return null if not found or empty
   */
  private String extractString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    String str = String.valueOf(value);
    return str.isEmpty() || "null".equals(str) ? null : str;
  }

  /**
   * Normalize config before saving to ensure correct format:
   * - deviceId must be String (not Long)
   * - tokenExpiresAt must be ISO8601 String (not number or Instant)
   */
  private void normalizeConfig(DeviceConfig config) {
    // Ensure deviceId is String
    if (config.getDeviceId() != null) {
      // If it's already a string, try to parse it to ensure it's valid
      try {
        Long.parseLong(config.getDeviceId());
        // Valid number string, keep it
      } catch (NumberFormatException e) {
        // Not a valid number, might be corrupted, try to fix
        log.warn("[device-config] deviceId is not a valid number string: {}, keeping as is", config.getDeviceId());
      }
    }
    
    // Ensure tokenExpiresAt is ISO8601 String
    if (config.getTokenExpiresAt() != null && !config.getTokenExpiresAt().isBlank()) {
      // Try to parse as ISO8601, if fails, try to convert from number string
      try {
        Instant.parse(config.getTokenExpiresAt());
        // Valid ISO8601, keep it
      } catch (Exception e) {
        // Not valid ISO8601, try to parse as number string (backward compatibility)
        try {
          String oldValue = config.getTokenExpiresAt();
          double timestamp = Double.parseDouble(oldValue);
          // Convert to Instant: treat as seconds with fractional part
          long seconds = (long) timestamp;
          long nanos = (long) ((timestamp - seconds) * 1e9);
          Instant instant = Instant.ofEpochSecond(seconds, nanos);
          config.setTokenExpiresAtFromInstant(instant);
          log.info("[device-config] Normalized tokenExpiresAt from number string {} to ISO8601: {}", 
              oldValue, config.getTokenExpiresAt());
        } catch (Exception ex) {
          log.warn("[device-config] Failed to normalize tokenExpiresAt: {}, keeping as is", config.getTokenExpiresAt());
        }
      }
    }
  }
}
