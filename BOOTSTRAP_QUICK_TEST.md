# 启动自动同步 - 快速验收指南

## 📋 文件清单

### 新增文件
1. `src/main/java/com/mg/booth/device/DeviceIdentity.java`
2. `src/main/java/com/mg/booth/device/DeviceIdentityStore.java`
3. `src/main/java/com/mg/booth/device/DeviceBootstrapRunner.java`
4. `device.json.example`

### 修改文件
1. `src/main/java/com/mg/booth/device/PlatformDeviceApiClient.java` - 新增 `listActivities()` 方法

## 🚀 快速验收（2 个场景）

### 场景 1：无 device.json（必须能启动）

**步骤**：
1. 确保运行目录下**没有** `device.json`
2. 启动 MVP：`mvn spring-boot:run`

**在控制台搜索关键字**：
```
[device] device.json not found
```

**预期**：
- ✅ MVP 正常启动（无异常）
- ✅ 看到 WARN 日志：`device.json not found at ... Skip platform sync.`

---

### 场景 2：有 device.json 且平台可用

**步骤**：
1. 在运行目录创建 `device.json`：
   ```json
   {
     "platformBaseUrl": "http://127.0.0.1:8089",
     "deviceCode": "dev_001",
     "secret": "dev_001_secret",
     "deviceId": null,
     "deviceToken": null,
     "tokenExpiresAt": null
   }
   ```
2. 确保 Platform 运行在 `http://127.0.0.1:8089`
3. 确保数据库有设备：`deviceCode="dev_001", secret="dev_001_secret"`
4. 启动 MVP：`mvn spring-boot:run`

**在控制台搜索关键字**：
```
[device] No valid token
[device] Handshake OK
[device] activities.size
[device] activity: id=
```

**预期**：
- ✅ MVP 正常启动
- ✅ 看到：`No valid token, handshake start`
- ✅ 看到：`Handshake OK. deviceId=...`
- ✅ 看到：`activities.size=...`
- ✅ 看到逐条活动日志：`activity: id=... name=...`
- ✅ `device.json` 被更新（包含 deviceId 和 deviceToken）

---

## 🔍 日志关键字速查

### 成功日志
- `[device] Handshake OK` - 握手成功
- `[device] activities.size=` - 活动数量
- `[device] activity: id=` - 活动详情

### 配置缺失（WARN，不影响启动）
- `[device] device.json not found` - 文件不存在
- `[device] deviceCode/secret not configured` - 字段缺失
- `[device] platformBaseUrl not configured` - URL 未配置

### 错误（ERROR，不影响启动）
- `[device] Handshake failed (non-fatal)` - 握手失败
- `[device] List activities failed (non-fatal)` - 拉取失败
- `[device] platform sync failed (non-fatal)` - 同步失败

---

## ✅ 验收通过标准

**场景 1（无 device.json）**：
- ✅ MVP 启动成功
- ✅ 日志出现 `device.json not found`
- ✅ 无异常堆栈

**场景 2（有 device.json）**：
- ✅ MVP 启动成功
- ✅ 日志出现 `Handshake OK`
- ✅ 日志出现 `activities.size=`
- ✅ `device.json` 被更新

---

## 🐛 如果失败

### 问题：还是启动失败
**检查**：确保所有异常都被 try-catch 包裹，没有 throw 出 ApplicationRunner

### 问题：看不到日志
**检查**：日志级别设置为 INFO 或更低

### 问题：handshake 失败
**检查**：
1. Platform 是否运行
2. URL 是否正确（`http://127.0.0.1:8089`）
3. 数据库是否有设备记录
