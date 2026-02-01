package com.mg.booth.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Handshake 测试 Runner（默认禁用）
 * 
 * 启用方式：在 application.yml 中添加：
 * test:
 *   handshake:
 *     enabled: true
 *     baseUrl: "http://127.0.0.1:8089"
 *     deviceCode: "dev_001"
 *     secret: "dev_001_secret"
 */
@Component
@ConditionalOnProperty(name = "test.handshake.enabled", havingValue = "true", matchIfMissing = false)
public class HandshakeTestRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(HandshakeTestRunner.class);

  private final PlatformDeviceApiClient client;

  @Value("${test.handshake.baseUrl:http://127.0.0.1:8089}")
  private String baseUrl;

  @Value("${test.handshake.deviceCode:dev_001}")
  private String deviceCode;

  @Value("${test.handshake.secret:dev_001_secret}")
  private String secret;

  public HandshakeTestRunner(PlatformDeviceApiClient client) {
    this.client = client;
  }

  @Override
  public void run(String... args) {
    log.info("[test] Handshake test runner started");
    log.info("[test] baseUrl={}, deviceCode={}", baseUrl, deviceCode);

    try {
      HandshakeData result = client.handshake(baseUrl, deviceCode, secret);
      log.info("[test] Handshake success!");
      log.info("[test] deviceId={}", result.deviceId());
      log.info("[test] deviceToken={}", result.deviceToken());
      log.info("[test] tokenExpiresAt={}", result.tokenExpiresAt());
    } catch (Exception e) {
      log.error("[test] Handshake failed: {}", e.getMessage(), e);
    }
  }
}
