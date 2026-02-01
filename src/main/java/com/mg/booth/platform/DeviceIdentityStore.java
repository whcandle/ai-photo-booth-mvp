package com.mg.booth.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class DeviceIdentityStore {
  private static final Logger log = LoggerFactory.getLogger(DeviceIdentityStore.class);

  private final ObjectMapper om = new ObjectMapper();

  /**
   * 加载 device.json，如果文件不存在或配置缺失，返回 Optional.empty()，不抛异常
   */
  public Optional<DeviceIdentity> load(Path file) {
    try {
      if (!Files.exists(file)) {
        log.warn("[device] device.json not found at {}. Skip platform sync.", file.toAbsolutePath());
        return Optional.empty();
      }
      var json = Files.readString(file);
      var id = om.readValue(json, DeviceIdentity.class);

      // 检查必填字段，但不抛异常
      if (id.getDeviceCode() == null || id.getDeviceCode().isBlank()) {
        log.warn("[device] deviceCode is missing in {}. Skip platform sync.", file.toAbsolutePath());
        return Optional.empty();
      }
      if (id.getSecret() == null || id.getSecret().isBlank()) {
        log.warn("[device] secret is missing in {}. Skip platform sync.", file.toAbsolutePath());
        return Optional.empty();
      }
      return Optional.of(id);
    } catch (Exception e) {
      log.warn("[device] Failed to load device identity from {}: {}. Skip platform sync.", 
          file.toAbsolutePath(), e.getMessage());
      return Optional.empty();
    }
  }

  public void save(Path file, DeviceIdentity id) {
    try {
      var json = om.writerWithDefaultPrettyPrinter().writeValueAsString(id);
      Files.writeString(file, json);
      log.info("[device] device.json updated: {}", file.toAbsolutePath());
    } catch (Exception e) {
      log.error("[device] Failed to save device identity to {}: {}", file.toAbsolutePath(), e.getMessage(), e);
      // 保存失败不抛异常，只记录日志
    }
  }
}
