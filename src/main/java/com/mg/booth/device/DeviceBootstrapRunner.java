package com.mg.booth.device;

import com.mg.booth.config.BoothProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备启动自动同步 Runner
 * 
 * 功能：
 * - 读取 device.json
 * - 必要时执行 handshake
 * - 拉取 activities 列表
 * - 打印日志
 * 
 * 特性：
 * - 没有 device.json 或配置缺失不会导致启动失败
 * - 平台不可用不会导致启动失败
 * - 所有异常都被捕获，只记录日志
 */
@Configuration
public class DeviceBootstrapRunner {

  private static final Logger log = LoggerFactory.getLogger(DeviceBootstrapRunner.class);

  @Bean
  public ApplicationRunner deviceBootstrapApplicationRunner(
      BoothProps props,
      @Qualifier("deviceDeviceIdentityStore") DeviceIdentityStore store,
      @Qualifier("devicePlatformDeviceApiClient") PlatformDeviceApiClient client) {
    return args -> {
      try {
        // A) 检查 platformBaseUrl 配置
        String platformBaseUrl = props.getPlatformBaseUrl();
        if (platformBaseUrl == null || platformBaseUrl.isBlank()) {
          log.warn("[device] platformBaseUrl not configured, skip platform sync.");
          return;
        }

        // B) 加载 device.json
        Path file = Path.of(props.getDeviceIdentityFile());
        Optional<DeviceIdentity> optId = store.load(file);
        
        if (optId.isEmpty()) {
          log.warn("[device] device.json not found or invalid, skip platform sync.");
          return;
        }

        DeviceIdentity id = optId.get();

        // C) 检查必填字段
        if (id.getDeviceCode() == null || id.getDeviceCode().isBlank()
            || id.getSecret() == null || id.getSecret().isBlank()) {
          log.warn("[device] deviceCode/secret not configured in {}, skip platform sync.", 
              file.toAbsolutePath());
          return;
        }

        // 使用 device.json 中的 platformBaseUrl，如果没有则使用配置的
        String baseUrl = (id.getPlatformBaseUrl() != null && !id.getPlatformBaseUrl().isBlank())
            ? id.getPlatformBaseUrl()
            : platformBaseUrl;

        // D) 检查是否需要 handshake
        if (id.getDeviceId() == null || !store.isTokenValid(id)) {
          log.info("[device] No valid token, handshake start. deviceCode={}", id.getDeviceCode());
          
          try {
            HandshakeData hsData = client.handshake(baseUrl, id.getDeviceCode(), id.getSecret());
            id.setDeviceId(hsData.deviceId());
            id.setDeviceToken(hsData.deviceToken());
            id.setTokenExpiresAt(hsData.tokenExpiresAt());
            store.save(file, id);
            log.info("[device] Handshake OK. deviceId={}, tokenExpiresAt={}", 
                id.getDeviceId(), id.getTokenExpiresAt());
          } catch (Exception e) {
            log.error("[device] Handshake failed (non-fatal): {}", e.getMessage(), e);
            return; // handshake 失败，不再继续拉取 activities
          }
        } else {
          log.info("[device] Found valid token. deviceId={}, tokenExpiresAt={}", 
              id.getDeviceId(), id.getTokenExpiresAt());
        }

        // E) 拉取 activities
        try {
          List<Map<String, Object>> activities = client.listActivities(
              baseUrl, id.getDeviceId(), id.getDeviceToken());
          
          log.info("[device] activities.size={}", activities.size());
          
          for (Map<String, Object> activity : activities) {
            log.info("[device] activity: id={} name={} status={} startAt={} endAt={}",
                activity.get("activityId"),
                activity.get("name"),
                activity.get("status"),
                activity.get("startAt"),
                activity.get("endAt"));
          }
        } catch (Exception e) {
          log.error("[device] List activities failed (non-fatal): {}", e.getMessage(), e);
          // 不抛异常，允许服务继续运行
        }

      } catch (Exception e) {
        // ✅ 关键：捕获所有异常，保证 MVP 继续启动
        log.error("[device] platform sync failed (non-fatal). mvp will continue running: {}", 
            e.getMessage(), e);
      }
    };
  }
}
