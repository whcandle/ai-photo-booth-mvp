# 快速验收指南

## 3 步完成验收

### 第 1 步：准备 Platform 服务

确保 Platform 服务运行在 `http://127.0.0.1:8089`

```bash
# 检查 Platform 是否运行
curl http://127.0.0.1:8089/api/v1/device/handshake -X POST -H "Content-Type: application/json" -d "{\"deviceCode\":\"test\",\"secret\":\"test\"}"
```

如果返回 JSON（无论成功失败），说明服务在运行。

### 第 2 步：准备测试数据

确保数据库中有设备记录：

```sql
-- 如果还没有，执行这个（假设 merchant_id=1 存在）
INSERT INTO devices (merchant_id, device_code, secret, name, status)
VALUES (1, 'dev_001', 'dev_001_secret', 'Test Device', 'ACTIVE');
```

### 第 3 步：运行测试

**方式 A：在 IDEA 中运行（最简单）**

1. 打开 `src/test/java/com/mg/booth/device/HandshakeQuickTest.java`
2. 右键点击类名或方法名
3. 选择 "Run 'HandshakeQuickTest'"

**方式 B：命令行运行**

```bash
cd d:\workspace\ai-photo-booth-mvp
mvn test -Dtest=HandshakeQuickTest
```

## 预期输出

如果一切正常，你会看到：

```
=== 开始验收测试 ===
✓ 客户端创建成功
✓ 参数准备完成: baseUrl=http://127.0.0.1:8089, deviceCode=dev_001

正在调用 handshake...

=== 验收结果 ===
✓ handshake 成功！
  deviceId: 3
  deviceToken: eyJhbGciOiJIUzI1NiJ9...
  tokenExpiresAt: 2026-02-01T12:30:00Z

✅ 所有验收点通过！
```

## 如果失败

### 错误 1：403 Forbidden

```
❌ 验收失败: Handshake request failed: 403 : [no body]
```

**原因**：URL 路径错误或 Spring Security 拦截

**解决步骤**：
1. 确认 Platform 服务运行在 `http://127.0.0.1:8089`
2. 用 curl 测试 Platform 是否可访问：
   ```bash
   curl -X POST http://127.0.0.1:8089/api/v1/device/handshake -H "Content-Type: application/json" -d "{\"deviceCode\":\"dev_001\",\"secret\":\"dev_001_secret\"}"
   ```
3. 如果 curl 返回 403，检查 Platform 的 SecurityConfig 是否允许 `/api/v1/device/**` 访问
4. 如果 curl 成功但测试失败，检查测试代码中的 baseUrl 是否正确

### 错误 2：连接失败

```
❌ 验收失败: Handshake request failed: Connection refused
```

**解决**：Platform 服务未启动，先启动 Platform。

### 错误 3：设备不存在

```
❌ 验收失败: Handshake failed: success=false, message=Device not found
```

**解决**：执行上面的 SQL 插入设备记录。

### 错误 4：secret 不匹配

```
❌ 验收失败: Handshake failed: success=false, message=Invalid secret
```

**解决**：检查数据库中的 secret 是否与测试代码中的一致。

## 修改测试参数

如果你的设备信息不同，修改 `HandshakeQuickTest.java` 中的：

```java
String baseUrl = "http://127.0.0.1:8089";  // 改成你的 Platform 地址
String deviceCode = "dev_001";            // 改成你的 deviceCode
String secret = "dev_001_secret";         // 改成你的 secret
```

## 验收通过标准

✅ **验收通过**：看到 "✅ 所有验收点通过！" 且没有异常

❌ **验收失败**：看到 "❌ 验收失败" 或测试抛出异常
