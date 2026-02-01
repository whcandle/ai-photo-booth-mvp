# Step B1 实现说明

## 概述

实现了 MVP 对接 Platform 的 Step B1 功能：
- 启动时自动读取 `device.json`（deviceCode/secret/deviceId/token）
- 如果 token/deviceId 为空，自动调用 Platform 的 handshake API
- 自动拉取 activities 列表并缓存
- 401 时自动重新握手并重试
- 提供 `/api/v1/platform/activities` 端点供 kiosk 查询
- **优雅降级**：即使 device.json 不存在或配置缺失，MVP 也能正常启动（只打 WARN 日志，不阻塞启动）

## 新增文件

### 配置类
- `src/main/java/com/mg/booth/config/BoothProps.java` - 添加了 `platformBaseUrl` 和 `deviceIdentityFile` 配置

### DTO 类
- `src/main/java/com/mg/booth/platform/dto/ApiResponse.java` - 统一响应包装
- `src/main/java/com/mg/booth/platform/dto/HandshakeRequest.java` - 握手请求
- `src/main/java/com/mg/booth/platform/dto/HandshakeData.java` - 握手响应数据
- `src/main/java/com/mg/booth/platform/dto/DeviceActivityDto.java` - 活动 DTO

### 核心类
- `src/main/java/com/mg/booth/platform/DeviceIdentity.java` - 设备身份信息
- `src/main/java/com/mg/booth/platform/DeviceIdentityStore.java` - device.json 读写
- `src/main/java/com/mg/booth/platform/PlatformDeviceApiClient.java` - Platform API 客户端
- `src/main/java/com/mg/booth/platform/PlatformSyncService.java` - 启动时自动同步服务
- `src/main/java/com/mg/booth/platform/PlatformConfigController.java` - 提供 activities 查询接口

## 配置说明

### application.yml

```yaml
booth:
  platformBaseUrl: "http://127.0.0.1:8080"  # Platform API 地址
  deviceIdentityFile: "device.json"  # device.json 文件路径（运行目录）
```

### device.json

在 MVP 运行目录（通常是项目根目录或 target 目录）创建 `device.json`：

```json
{
  "deviceCode": "dev_001",
  "secret": "dev_001_secret",
  "deviceId": null,
  "token": null
}
```

**注意**：
- `deviceCode` 和 `secret` 必须填写（与 Platform 数据库中的设备信息一致）
- `deviceId` 和 `token` 首次可以为 null，启动后会自动填充

## 运行步骤

### 1. 准备 device.json

在 MVP 运行目录创建 `device.json`（参考上面的格式）。

### 2. 确保 Platform 已启动

确保 `ai-photo-booth-platform` 已启动并可以访问：
- `POST http://localhost:8080/api/v1/device/handshake`
- `GET http://localhost:8080/api/v1/device/{deviceId}/activities`

### 3. 启动 MVP

```bash
mvn spring-boot:run
```

或使用 IDE 运行 `AiPhotoBoothApplication`。

### 4. 查看日志

启动时应该看到以下关键日志：

**首次启动（无 token）：**
```
[platform] No token/deviceId, handshake start. platformBaseUrl=http://127.0.0.1:8089
[device] device.json updated: D:\workspace\ai-photo-booth-mvp\device.json
[platform] Handshake OK. deviceId=3 expiresIn=86400 serverTime=2026-01-31T20:30:00+08:00
[platform] activities.size=1
[platform] activity: id=3 name=ccc status=ACTIVE startAt=null endAt=null
```

**后续启动（有 token）：**
```
[platform] Found cached token. deviceId=3
[platform] activities.size=1
[platform] activity: id=3 name=ccc status=ACTIVE startAt=null endAt=null
```

**401 自动重试：**
```
[platform] activities 401, re-handshake then retry once. msg=Invalid or unauthorized device token
[platform] No token/deviceId, handshake start...
[platform] Handshake OK. deviceId=3 expiresIn=86400 serverTime=...
[platform] activities.size=1
```

**配置缺失（不影响启动）：**
```
[device] device.json not found at D:\workspace\ai-photo-booth-mvp\device.json. Skip platform sync.
[platform] device.json not found or invalid, skip platform sync.
```
或
```
[device] deviceCode is missing in device.json. Skip platform sync.
[platform] deviceCode/secret not configured in device.json, skip handshake/activities.
```

**重要**：即使看到这些 WARN 日志，MVP 也会正常启动，只是不会执行平台同步功能。

## 验收测试

### 1. 验证 handshake 和 activities 拉取

查看启动日志，确认：
- ✅ handshake 成功
- ✅ device.json 已更新
- ✅ activities 列表已拉取并打印

### 2. 验证缓存接口

```bash
curl http://127.0.0.1:8080/api/v1/platform/activities
```

**预期响应：**
```json
[
  {
    "activityId": 3,
    "name": "ccc",
    "status": "ACTIVE",
    "startAt": null,
    "endAt": null
  }
]
```

如果返回空数组 `[]`，说明设备还没有绑定活动（这是正常的）。

### 3. 验证 device.json 更新

检查运行目录下的 `device.json`，应该包含：
```json
{
  "deviceCode": "dev_001",
  "secret": "dev_001_secret",
  "deviceId": 3,
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

## 数据库准备

确保 Platform 数据库中有对应的设备记录：

```sql
-- 假设 merchant_id = 1 存在
INSERT INTO devices (merchant_id, device_code, secret, name, status)
VALUES (1, 'dev_001', 'dev_001_secret', 'Test Device', 'ACTIVE');

-- 绑定活动（可选，用于测试 activities 拉取）
INSERT INTO activities (merchant_id, name, status, start_at, end_at)
VALUES (1, '测试活动', 'ACTIVE', NULL, NULL);

INSERT INTO device_activity_assignments (device_id, activity_id, status)
VALUES (3, 3, 'ACTIVE');
```

## 注意事项

1. **device.json 路径**：默认在运行目录，可通过 `booth.deviceIdentityFile` 配置修改
2. **Platform URL**：确保 `platformBaseUrl` 配置正确，指向 Platform 服务地址（默认 8089 端口）
3. **401 自动重试**：如果 activities 接口返回 401，会自动清空 token 并重新握手
4. **日志级别**：建议保持 INFO 级别，可以看到关键日志
5. **kiosk 集成**：kiosk 可以通过 `GET /api/v1/platform/activities` 获取活动列表，无需直接调用 Platform
6. **优雅降级**：**即使 device.json 不存在或配置缺失，MVP 也能正常启动**，只是不会执行平台同步功能，会在日志中显示 WARN 提示
7. **现场维护友好**：新装机、配置丢失、JSON 损坏等情况都不会导致服务无法启动，便于现场排障

## 下一步

Step B1 完成后，可以继续：
- Step B2：实现模板下载和缓存
- Step B3：kiosk UI 集成（活动选择、模板展示等）
