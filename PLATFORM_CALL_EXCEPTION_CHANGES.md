# PlatformCallException 异常处理重构

## 📋 修改内容

### 1. 新增异常类：`PlatformCallException.java`

**位置**：`src/main/java/com/mg/booth/device/PlatformCallException.java`

**字段**：
- `int httpStatus` - HTTP 状态码（没有则 -1）
- `String url` - 请求的 URL
- `String reason` - 错误原因（"unauthorized" / "timeout" / "dns" / "connection_refused" / "http_error" / "unreachable"）
- `Object responseBody` - 响应体（可选）

**辅助方法**：
- `isUnauthorized()` - 检查是否是 401 未授权
- `isUnreachable()` - 检查是否是 503 服务不可达

---

### 2. 修改 `PlatformDeviceApiClient.java`

#### 2.1 baseUrl 验证
- **handshake()** 和 **listActivities()** 方法开始处检查 baseUrl
- 如果 baseUrl 为空，直接抛出 `IllegalArgumentException("platformBaseUrl not configured")`
- 不再生成相对路径请求

#### 2.2 异常处理（handshake 方法）

**捕获 `HttpStatusCodeException`**：
- 提取 `statusCode` 和 `responseBody`
- 如果 statusCode == 401，reason = "unauthorized"
- 否则 reason = "http_error"
- 抛出 `PlatformCallException(statusCode, url, reason, message, responseBody)`

**捕获 `ResourceAccessException`**：
- 识别错误类型：
  - 包含 "timeout" → reason = "timeout"
  - 包含 "unknownhost" 或 "dns" → reason = "dns"
  - 包含 "connection refused" 或 "connectionreset" → reason = "connection_refused"
  - 其他 → reason = "unreachable"
- 抛出 `PlatformCallException(503, url, reason, message)`

**捕获其他 `RestClientException`**：
- 抛出 `PlatformCallException(503, url, "unreachable", message)`

#### 2.3 异常处理（listActivities 方法）
- 与 handshake 方法相同的异常处理逻辑

#### 2.4 日志记录
- 所有异常都记录：`url` + `status` + `reason`
- **不打印 secret**（只在 debug 级别记录 deviceCode）

---

## 📝 异常类型映射

| 异常类型 | HTTP 状态码 | reason | 说明 |
|---------|------------|--------|------|
| HttpStatusCodeException (401) | 401 | "unauthorized" | 未授权 |
| HttpStatusCodeException (其他) | 实际状态码 | "http_error" | HTTP 错误 |
| ResourceAccessException (timeout) | 503 | "timeout" | 超时 |
| ResourceAccessException (DNS) | 503 | "dns" | DNS 解析失败 |
| ResourceAccessException (connection refused) | 503 | "connection_refused" | 连接被拒绝 |
| ResourceAccessException (其他) | 503 | "unreachable" | 服务不可达 |
| 其他 RestClientException | 503 | "unreachable" | 其他网络错误 |
| baseUrl 为空 | IllegalArgumentException | - | 配置错误 |

---

## 🔄 调用方影响

### DeviceBootstrapRunner
- **当前**：捕获所有 `Exception`，记录日志，不抛异常（non-fatal）
- **影响**：无需修改，可以继续捕获 `PlatformCallException` 和 `IllegalArgumentException`

### DeviceProxyController
- **当前**：捕获所有 `Exception`，返回错误响应
- **建议**：可以检查 `PlatformCallException`，根据 `httpStatus` 返回对应的 HTTP 状态码

---

## ✅ 验收点

- [x] baseUrl 为空时抛出 `IllegalArgumentException`
- [x] `HttpStatusCodeException` 转换为 `PlatformCallException`，包含 statusCode 和 responseBody
- [x] 401 错误 reason = "unauthorized"
- [x] `ResourceAccessException` 转换为 `PlatformCallException(503, "unreachable" 或更具体的 reason)`
- [x] 其他 `RestClientException` 转换为 `PlatformCallException(503, "unreachable")`
- [x] 日志记录 url + status + reason，不打印 secret
- [x] 编译通过

---

## 📌 使用示例

```java
try {
    HandshakeData data = client.handshake(baseUrl, deviceCode, secret);
} catch (PlatformCallException e) {
    if (e.isUnauthorized()) {
        // 处理 401 未授权
    } else if (e.isUnreachable()) {
        // 处理 503 服务不可达
    }
    // 获取详细信息
    int status = e.getHttpStatus();
    String reason = e.getReason();
    Object responseBody = e.getResponseBody();
} catch (IllegalArgumentException e) {
    // 处理 baseUrl 未配置
}
```
