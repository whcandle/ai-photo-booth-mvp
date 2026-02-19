package com.mg.booth.platform;

import com.mg.booth.config.BoothProps;
import com.mg.booth.platform.dto.DeviceActivityDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Deprecated
@Service
public class PlatformSyncService {
  private static final Logger log = LoggerFactory.getLogger(PlatformSyncService.class);

  private final BoothProps props;
  private final DeviceIdentityStore store;
  private final PlatformDeviceApiClient client;

  private final AtomicReference<List<DeviceActivityDto>> cachedActivities = new AtomicReference<>(List.of());

  public PlatformSyncService(BoothProps props, DeviceIdentityStore store, PlatformDeviceApiClient client) {
    this.props = props;
    this.store = store;
    this.client = client;
  }

  public List<DeviceActivityDto> getCachedActivities() {
    return cachedActivities.get();
  }

  // 已禁用：功能已迁移到 device.DeviceBootstrapRunner
  // @Bean
  public ApplicationRunner platformBootstrapRunner() {
    return args -> {
      try {
        // 检查 platformBaseUrl 配置
        if (props.getPlatformBaseUrl() == null || props.getPlatformBaseUrl().isBlank()) {
          log.warn("[platform] platformBaseUrl not configured, skip handshake/activities.");
          return;
        }

        Path file = Path.of(props.getDeviceIdentityFile());
        Optional<PlatformDeviceSession> optId = store.load(file);
        
        if (optId.isEmpty()) {
          log.warn("[platform] device.json not found or invalid, skip platform sync.");
          return;
        }

        PlatformDeviceSession id = optId.get();

        // 检查 deviceCode 和 secret
        if (id.getDeviceCode() == null || id.getDeviceCode().isBlank()
            || id.getSecret() == null || id.getSecret().isBlank()) {
          log.warn("[platform] deviceCode/secret not configured in {}, skip handshake/activities.", 
              file.toAbsolutePath());
          return;
        }

        ensureHandshake(file, id);

        // 拉活动（若 401，重握手再拉一次）
        pullActivitiesWithRetry(file, id);

      } catch (Exception e) {
        // ✅ 关键：吞掉异常，保证 MVP 继续启动
        log.error("[platform] bootstrap failed but mvp will continue running: {}", e.getMessage(), e);
      }
    };
  }

  private void ensureHandshake(Path file, PlatformDeviceSession id) {
    try {
      if (id.getToken() == null || id.getToken().isBlank() || id.getDeviceId() == null) {
        log.info("[platform] No token/deviceId, handshake start. platformBaseUrl={}", props.getPlatformBaseUrl());
        var data = client.handshake(props.getPlatformBaseUrl(), id.getDeviceCode(), id.getSecret());
        id.setDeviceId(data.getDeviceId());
        id.setToken(data.getDeviceToken());
        store.save(file, id);
        log.info("[platform] Handshake OK. deviceId={} expiresIn={} serverTime={}",
            id.getDeviceId(), data.getExpiresIn(), data.getServerTime());
      } else {
        log.info("[platform] Found cached token. deviceId={}", id.getDeviceId());
      }
    } catch (Exception e) {
      log.error("[platform] Handshake failed (non-fatal): {}", e.getMessage(), e);
      // 不抛异常，允许服务继续运行
    }
  }

  private void pullActivitiesWithRetry(Path file, PlatformDeviceSession id) {
    try {
      pullActivities(id);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        log.warn("[platform] activities 401, re-handshake then retry once. msg={}", e.getMessage());
        try {
          // 清 token 触发握手
          id.setToken(null);
          id.setDeviceId(null);
          ensureHandshake(file, id);
          // 只有握手成功才重试拉取
          if (id.getDeviceId() != null && id.getToken() != null) {
            pullActivities(id);
          }
        } catch (Exception retryEx) {
          log.error("[platform] Retry after 401 failed: {}", retryEx.getMessage(), retryEx);
        }
        return;
      }
      log.error("[platform] pull activities failed: {}", e.getMessage(), e);
    } catch (Exception e) {
      log.error("[platform] pull activities failed (non-fatal): {}", e.getMessage(), e);
      // 不抛异常，允许服务继续运行
    }
  }

  private void pullActivities(PlatformDeviceSession id) {
    var acts = client.listActivities(props.getPlatformBaseUrl(), id.getDeviceId(), id.getToken());
    cachedActivities.set(acts);

    log.info("[platform] activities.size={}", acts.size());
    for (var a : acts) {
      log.info("[platform] activity: id={} name={} status={} startAt={} endAt={}",
          a.getActivityId(), a.getName(), a.getStatus(), a.getStartAt(), a.getEndAt());
    }
  }
}
