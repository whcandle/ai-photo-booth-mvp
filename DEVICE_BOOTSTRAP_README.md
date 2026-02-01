# Device Bootstrap 启动自动同步说明

## 新增/修改文件清单

### 新增文件（4 个）

1. **`src/main/java/com/mg/booth/device/DeviceIdentity.java`**
   - 设备身份信息 POJO
   - 字段：platformBaseUrl, deviceCode, secret, deviceId, deviceToken, tokenExpiresAt

2. **`src/main/java/com/mg/booth/device/DeviceIdentityStore.java`**
   - device.json 读写工具
   - 提供 load()、save()、isTokenValid() 方法

3. **`src/main/java/com/mg/booth/device/DeviceBootstrapRunner.java`**
   - 启动自动同步 Runner
   - 实现 ApplicationRunner，启动时自动执行

4. **`device.json.example`**
   - device.json 示例文件

### 修改文件（1 个）

1. **`src/main/java/com/mg/booth/device/PlatformDeviceApiClient.java`**
   - 新增 `listActivities()` 方法

## device.json 示例

在 MVP 运行目录（jar 同级目录）创建 `device.json`：

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

**说明**：
- `platformBaseUrl`、`deviceCode`、`secret` 为必填
- `deviceId`、`deviceToken`、`tokenExpiresAt` 首次可以为 null，handshake 后会自动填充

## 日志关键字（在控制台搜索这些）

### 成功场景

- `[device] Handshake OK. deviceId=...` - 握手成功
- `[device] activities.size=...` - 活动数量
- `[device] activity: id=... name=...` - 活动详情

### 配置缺失场景

- `[device] device.json not found at ...` - 文件不存在
- `[device] deviceCode/secret not configured ...` - 字段缺失
- `[device] platformBaseUrl not configured ...` - URL 未配置

### 错误场景（非致命）

- `[device] Handshake failed (non-fatal): ...` - 握手失败
- `[device] List activities failed (non-fatal): ...` - 拉取活动失败
- `[device] platform sync failed (non-fatal) ...` - 同步失败

## 验收测试

### 验收 1：无 device.json（MVP 必须能启动）

**步骤**：
1. 确保运行目录下**没有** `device.json` 文件
2. 启动 MVP：`mvn spring-boot:run`

**预期结果**：
- ✅ MVP 正常启动（不报错）
- ✅ 日志中出现：`[device] device.json not found at ... Skip platform sync.`
- ✅ 没有异常堆栈

### 验收 2：有 device.json 且平台可用

**步骤**：
1. 在运行目录创建 `device.json`（参考上面的示例）
2. 确保 Platform 服务运行在 `http://127.0.0.1:8089`
3. 确保数据库中有设备记录（deviceCode="dev_001", secret="dev_001_secret"）
4. 启动 MVP：`mvn spring-boot:run`

**预期结果**：
- ✅ MVP 正常启动
- ✅ 日志中出现：`[device] No valid token, handshake start ...`
- ✅ 日志中出现：`[device] Handshake OK. deviceId=...`
- ✅ 日志中出现：`[device] activities.size=...`
- ✅ 日志中逐条打印活动：`[device] activity: id=... name=...`
- ✅ `device.json` 文件被更新，包含 deviceId 和 deviceToken

### 验收 3：有 device.json 但平台不可用

**步骤**：
1. 在运行目录创建 `device.json`
2. **不启动** Platform 服务（或使用错误的 URL）
3. 启动 MVP：`mvn spring-boot:run`

**预期结果**：
- ✅ MVP 正常启动（不报错）
- ✅ 日志中出现：`[device] Handshake failed (non-fatal): ...`
- ✅ 没有异常导致启动失败

### 验收 4：有 device.json 但字段不齐

**步骤**：
1. 在运行目录创建 `device.json`，但缺少 `deviceCode` 或 `secret`：
   ```json
   {
     "platformBaseUrl": "http://127.0.0.1:8089"
   }
   ```
2. 启动 MVP：`mvn spring-boot:run`

**预期结果**：
- ✅ MVP 正常启动
- ✅ 日志中出现：`[device] deviceCode/secret not configured ... Skip platform sync.`

## 快速验收命令

### 测试 1：无 device.json

```bash
# 删除 device.json（如果存在）
rm device.json

# 启动 MVP
mvn spring-boot:run

# 在日志中搜索
grep -i "device.json not found" logs.txt
```

### 测试 2：有 device.json

```bash
# 创建 device.json
cat > device.json << 'EOF'
{
  "platformBaseUrl": "http://127.0.0.1:8089",
  "deviceCode": "dev_001",
  "secret": "dev_001_secret",
  "deviceId": null,
  "deviceToken": null,
  "tokenExpiresAt": null
}
EOF

# 启动 MVP
mvn spring-boot:run

# 在日志中搜索
grep -i "Handshake OK\|activities.size" logs.txt
```

## 注意事项

1. **device.json 路径**：默认在运行目录（`System.getProperty("user.dir")`），可通过 `booth.deviceIdentityFile` 配置修改

2. **platformBaseUrl 优先级**：
   - 优先使用 `device.json` 中的 `platformBaseUrl`
   - 如果为空，使用 `application.yml` 中的 `booth.platformBaseUrl`

3. **Token 有效性**：token 过期前 30 秒会重新握手

4. **非阻塞设计**：所有异常都被捕获，不会导致启动失败

5. **日志级别**：建议保持 INFO 级别，可以看到所有关键日志
