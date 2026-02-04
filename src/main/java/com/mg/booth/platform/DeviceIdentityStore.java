package com.mg.booth.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @deprecated This class is kept for backward compatibility with PlatformSyncService (which is disabled).
 * For device.json operations, use DeviceConfig and DeviceConfigStore instead.
 */
@Deprecated
@Component
public class DeviceIdentityStore {
  private static final Logger log = LoggerFactory.getLogger(DeviceIdentityStore.class);

  private final ObjectMapper om = new ObjectMapper();

  /**
   * 加载 device.json，如果文件不存在或配置缺失，返回 Optional.empty()，不抛异常
   * 
   * @deprecated Use DeviceConfigStore.load() instead
   */
  @Deprecated
  public Optional<PlatformDeviceSession> load(Path file) {
    try {
      if (!Files.exists(file)) {
        log.warn("[device] device.json not found at {}. Skip platform sync.", file.toAbsolutePath());
        return Optional.empty();
      }
      var json = Files.readString(file);
      var id = om.readValue(json, PlatformDeviceSession.class);

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

  /**
   * @deprecated 已禁用：device.json 的唯一写入入口是 DeviceConfigStore（原子写）。
   * 此方法不再允许写入 device.json，以防止与 DeviceConfigStore 的写入冲突。
   * 如需写入 device.json，请使用 DeviceConfigStore.save()。
   */
  @Deprecated
  public void save(Path file, PlatformDeviceSession id) {
    log.error("[device] Platform DeviceIdentityStore.save() is deprecated and disabled. " +
        "Use DeviceConfigStore.save() instead to write device.json (atomic write). " +
        "Attempted save to: {}", file.toAbsolutePath());
    throw new UnsupportedOperationException(
        "Platform DeviceIdentityStore.save() is disabled. Use DeviceConfigStore.save() instead.");
  }
}
