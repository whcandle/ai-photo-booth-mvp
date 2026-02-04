package com.mg.booth.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * device.json 读写工具
 */
@Component("deviceDeviceIdentityStore")
public class DeviceIdentityStore {
  private static final Logger log = LoggerFactory.getLogger(DeviceIdentityStore.class);

  private final ObjectMapper om;

  public DeviceIdentityStore() {
    this.om = new ObjectMapper();
    this.om.registerModule(new JavaTimeModule());
  }

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
  public void save(Path file, DeviceIdentity id) {
    log.error("[device] DeviceIdentityStore.save() is deprecated and disabled. " +
        "Use DeviceConfigStore.save() instead to write device.json (atomic write). " +
        "Attempted save to: {}", file.toAbsolutePath());
    throw new UnsupportedOperationException(
        "DeviceIdentityStore.save() is disabled. Use DeviceConfigStore.save() instead.");
  }

  /**
   * 检查 token 是否有效（存在且未过期，留 30 秒缓冲）
   */
  public boolean isTokenValid(DeviceIdentity id) {
    if (id.getDeviceToken() == null || id.getDeviceToken().isBlank()) {
      return false;
    }
    if (id.getTokenExpiresAt() == null) {
      return true; // 没有过期时间就当有效
    }
    return id.getTokenExpiresAt().isAfter(java.time.Instant.now().plusSeconds(30));
  }
}
