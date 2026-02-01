# PlatformDeviceApiClient 使用说明

## 新增文件

1. **`src/main/java/com/mg/booth/device/HandshakeData.java`**
   - Handshake 响应数据记录类
   - 包含：deviceId, deviceToken, tokenExpiresAt

2. **`src/main/java/com/mg/booth/device/PlatformDeviceApiClient.java`**
   - Platform 设备 API 客户端（仅实现 handshake）
   - 使用 Spring RestTemplate
   - 自动注入 RestTemplateBuilder

3. **`src/main/java/com/mg/booth/device/HandshakeTestRunner.java`**
   - 测试 Runner（默认禁用）
   - 可通过配置启用进行测试

4. **`src/test/java/com/mg/booth/device/PlatformDeviceApiClientTest.java`**
   - JUnit 集成测试

## 使用方法

### 1. 基本调用

```java
@Autowired
private PlatformDeviceApiClient client;

public void testHandshake() {
    String baseUrl = "http://127.0.0.1:8089";
    String deviceCode = "dev_001";
    String secret = "dev_001_secret";
    
    HandshakeData result = client.handshake(baseUrl, deviceCode, secret);
    
    System.out.println("deviceId: " + result.deviceId());
    System.out.println("deviceToken: " + result.deviceToken());
    System.out.println("tokenExpiresAt: " + result.tokenExpiresAt());
}
```

### 2. 启用测试 Runner

在 `application.yml` 中添加：

```yaml
test:
  handshake:
    enabled: true
    baseUrl: "http://127.0.0.1:8089"
    deviceCode: "dev_001"
    secret: "dev_001_secret"
```

启动应用时会自动执行 handshake 并打印结果。

### 3. 运行 JUnit 测试

```bash
mvn test -Dtest=PlatformDeviceApiClientTest
```

**注意**：测试需要 Platform 服务运行，否则会失败。

## API 说明

### handshake 方法

```java
HandshakeData handshake(String baseUrl, String deviceCode, String secret)
```

**参数：**
- `baseUrl`: Platform API 基础 URL（例如：`http://127.0.0.1:8089`）
- `deviceCode`: 设备编码
- `secret`: 设备密钥

**返回：**
- `HandshakeData`: 包含 deviceId, deviceToken, tokenExpiresAt

**异常：**
- `RuntimeException`: 如果 HTTP 非 2xx 或 success != true

**特性：**
- 自动规范化 baseUrl（去掉末尾的 `/`）
- 完整的错误处理
- 日志记录

## 验收测试

### 测试 1：正常 handshake

```bash
# 确保 Platform 运行在 http://127.0.0.1:8089
# 确保数据库中有设备：deviceCode="dev_001", secret="dev_001_secret"

# 方式 1：使用测试 Runner
# 在 application.yml 中启用 test.handshake.enabled=true
# 启动应用，查看日志

# 方式 2：运行 JUnit 测试
mvn test -Dtest=PlatformDeviceApiClientTest#testHandshake
```

### 测试 2：baseUrl 末尾有 `/`

```bash
# 测试 baseUrl 规范化
mvn test -Dtest=PlatformDeviceApiClientTest#testHandshakeWithTrailingSlash
```

### 测试 3：无效凭证

```bash
# 测试错误处理
mvn test -Dtest=PlatformDeviceApiClientTest#testHandshakeWithInvalidCredentials
```

## 响应格式

Platform 返回格式：

```json
{
  "success": true,
  "data": {
    "deviceId": 1,
    "deviceToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 86400,
    "serverTime": "2026-01-31T20:30:00+08:00"
  },
  "message": "Success"
}
```

`HandshakeData` 映射：
- `deviceId` ← `data.deviceId`
- `deviceToken` ← `data.deviceToken`
- `tokenExpiresAt` ← `Instant.now() + expiresIn`（秒）

## 注意事项

1. **baseUrl 规范化**：方法会自动去掉末尾的 `/`，所以 `http://127.0.0.1:8089` 和 `http://127.0.0.1:8089/` 都可以正常使用

2. **错误处理**：
   - HTTP 非 2xx：抛出 RuntimeException，包含状态码和响应体
   - success != true：抛出 RuntimeException，包含错误信息
   - 响应格式错误：抛出 RuntimeException，说明具体问题

3. **日志**：使用 SLF4J，日志级别为 DEBUG（请求）和 INFO（成功）

4. **依赖**：需要 Spring Boot Web Starter（已包含 RestTemplate）
