package com.mg.booth.device;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlatformDeviceApiClient 测试
 * 
 * 注意：这是集成测试，需要 Platform 服务运行
 * 
 * 运行前确保：
 * 1. Platform 服务已启动（默认 http://127.0.0.1:8089）
 * 2. 数据库中有对应的设备记录
 * 
 * 如果 Platform 未运行，测试会失败（这是预期的）
 */
@SpringBootTest
@TestPropertySource(properties = {
    "test.handshake.enabled=false" // 禁用 CommandLineRunner
})
class PlatformDeviceApiClientTest {

  @Test
  void testHandshake() {
    // 准备
    RestTemplateBuilder builder = new RestTemplateBuilder();
    PlatformDeviceApiClient client = new PlatformDeviceApiClient(builder);

    String baseUrl = "http://127.0.0.1:8089";
    String deviceCode = "dev_001";
    String secret = "dev_001_secret";

    // 执行
    HandshakeData result = client.handshake(baseUrl, deviceCode, secret);

    // 验证
    assertNotNull(result);
    assertNotNull(result.deviceId());
    assertNotNull(result.deviceToken());
    assertNotNull(result.tokenExpiresAt());
    assertTrue(result.deviceId() > 0);
    assertFalse(result.deviceToken().isBlank());
  }

  @Test
  void testHandshakeWithTrailingSlash() {
    // 测试 baseUrl 末尾有 / 的情况
    RestTemplateBuilder builder = new RestTemplateBuilder();
    PlatformDeviceApiClient client = new PlatformDeviceApiClient(builder);

    String baseUrl = "http://127.0.0.1:8089/"; // 末尾有 /
    String deviceCode = "dev_001";
    String secret = "dev_001_secret";

    // 执行
    HandshakeData result = client.handshake(baseUrl, deviceCode, secret);

    // 验证
    assertNotNull(result);
    assertNotNull(result.deviceId());
    assertNotNull(result.deviceToken());
  }

  @Test
  void testHandshakeWithInvalidCredentials() {
    // 测试无效凭证
    RestTemplateBuilder builder = new RestTemplateBuilder();
    PlatformDeviceApiClient client = new PlatformDeviceApiClient(builder);

    String baseUrl = "http://127.0.0.1:8089";
    String deviceCode = "invalid_device";
    String secret = "invalid_secret";

    // 执行并验证异常
    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      client.handshake(baseUrl, deviceCode, secret);
    });

    assertTrue(exception.getMessage().contains("Handshake failed"));
  }
}
