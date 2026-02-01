package com.mg.booth.device;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

/**
 * 快速验收测试 - 直接运行这个测试即可
 * 
 * 运行前确保：
 * 1. Platform 服务已启动（http://127.0.0.1:8089）
 * 2. 数据库中有设备：deviceCode="dev_001", secret="dev_001_secret"
 * 
 * 运行方式：
 * 右键 -> Run 'HandshakeQuickTest' 或
 * mvn test -Dtest=HandshakeQuickTest
 */
class HandshakeQuickTest {

  @Test
  void testHandshake() {
    System.out.println("=== 开始验收测试 ===");
    
    // 1. 创建客户端
    RestTemplateBuilder builder = new RestTemplateBuilder();
    PlatformDeviceApiClient client = new PlatformDeviceApiClient(builder);
    System.out.println("✓ 客户端创建成功");
    
    // 2. 准备参数（根据你的实际情况修改）
    String baseUrl = "http://127.0.0.1:8089";
    String deviceCode = "dev_001";
    String secret = "dev_001_secret";
    System.out.println("✓ 参数准备完成: baseUrl=" + baseUrl + ", deviceCode=" + deviceCode);
    
    // 3. 执行 handshake
    System.out.println("\n正在调用 handshake...");
    try {
      HandshakeData result = client.handshake(baseUrl, deviceCode, secret);
      
      // 4. 验证结果
      System.out.println("\n=== 验收结果 ===");
      System.out.println("✓ handshake 成功！");
      System.out.println("  deviceId: " + result.deviceId());
      System.out.println("  deviceToken: " + result.deviceToken().substring(0, Math.min(50, result.deviceToken().length())) + "...");
      System.out.println("  tokenExpiresAt: " + result.tokenExpiresAt());
      
      // 验证必填字段
      assert result.deviceId() != null : "deviceId 不能为空";
      assert result.deviceToken() != null && !result.deviceToken().isBlank() : "deviceToken 不能为空";
      assert result.tokenExpiresAt() != null : "tokenExpiresAt 不能为空";
      
      System.out.println("\n✅ 所有验收点通过！");
      
    } catch (Exception e) {
      System.err.println("\n❌ 验收失败: " + e.getMessage());
      e.printStackTrace();
      throw e;
    }
  }
  
  @Test
  void testBaseUrlNormalize() {
    System.out.println("=== 测试 baseUrl 规范化 ===");
    
    RestTemplateBuilder builder = new RestTemplateBuilder();
    PlatformDeviceApiClient client = new PlatformDeviceApiClient(builder);
    
    // 测试 baseUrl 末尾有 / 的情况
    String baseUrlWithSlash = "http://127.0.0.1:8089/";
    String deviceCode = "dev_001";
    String secret = "dev_001_secret";
    
    System.out.println("测试 baseUrl 末尾有 /: " + baseUrlWithSlash);
    
    try {
      HandshakeData result = client.handshake(baseUrlWithSlash, deviceCode, secret);
      System.out.println("✓ baseUrl 规范化测试通过！deviceId=" + result.deviceId());
    } catch (Exception e) {
      System.err.println("❌ baseUrl 规范化测试失败: " + e.getMessage());
      throw e;
    }
  }
}
