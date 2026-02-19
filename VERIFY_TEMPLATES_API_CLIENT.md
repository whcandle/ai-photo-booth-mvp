# 验证 PlatformDeviceApiClient.listActivityTemplates() 方法

## 验证方式

### 方式 1：直接测试 Platform API（推荐）

使用提供的 PowerShell 脚本测试 Platform API 端点，验证响应格式是否符合 API Client 的预期。

```powershell
# 基本用法（从 device.json 读取 token）
.\test_templates_api_client.ps1

# 指定参数
.\test_templates_api_client.ps1 -platformUrl "http://127.0.0.1:8089" -deviceId 1 -activityId 1 -deviceToken "your_token"
```

**验证点**：
- ✅ 响应格式：`{success, data, message}`
- ✅ `data` 是否为数组
- ✅ 每个 template 是否包含 `updatedAt` 字段
- ✅ 错误响应格式（401/403/404/500）

---

### 方式 2：使用 curl 手动测试

```bash
# 基本请求
curl -X GET \
  "http://127.0.0.1:8089/api/v1/device/1/activities/1/templates" \
  -H "Authorization: Bearer YOUR_DEVICE_TOKEN" \
  -H "Content-Type: application/json"

# 格式化输出（需要 jq）
curl -X GET \
  "http://127.0.0.1:8089/api/v1/device/1/activities/1/templates" \
  -H "Authorization: Bearer YOUR_DEVICE_TOKEN" \
  | jq .
```

**验证点**：
- ✅ HTTP 状态码
- ✅ 响应 JSON 结构
- ✅ `data` 数组内容

---

### 方式 3：在 DevicePlatformController 中集成后测试

在 `DevicePlatformController` 中新增 `/local/device/activities/{activityId}/templates` 接口，然后测试：

```powershell
# 测试本地代理接口（需要先实现）
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/1/templates" -Method GET
```

---

### 方式 4：单元测试（如果有测试框架）

创建单元测试类，mock RestTemplate，验证：
- URL 构建正确
- 请求头设置正确
- 响应解析正确
- 异常处理正确

---

## 验证清单

### ✅ 正常流程

- [ ] **成功响应**：
  - HTTP 200
  - `success=true`
  - `data` 是数组
  - 返回 `List<Map<String, Object>>`

- [ ] **空列表**：
  - HTTP 200
  - `success=true`
  - `data=null` 或 `data=[]`
  - 返回空 list

### ✅ 异常流程

- [ ] **401 Unauthorized**：
  - HTTP 401
  - 抛出 `PlatformCallException`
  - `reason="unauthorized"`
  - `isUnauthorized() == true`

- [ ] **403 Forbidden**：
  - HTTP 403
  - 抛出 `PlatformCallException`
  - `reason="http_error"`

- [ ] **404 Not Found**：
  - HTTP 404
  - 抛出 `PlatformCallException`
  - `reason="http_error"`

- [ ] **500 Internal Server Error**：
  - HTTP 500
  - 抛出 `PlatformCallException`
  - `reason="http_error"`

- [ ] **success=false**：
  - HTTP 200
  - `success=false`
  - 抛出 `PlatformCallException`
  - `reason="unauthorized"` 或 `"http_error"`（根据 message 判断）

- [ ] **网络错误（超时）**：
  - 抛出 `PlatformCallException`
  - `reason="timeout"`
  - `isUnreachable() == true`

- [ ] **网络错误（DNS）**：
  - 抛出 `PlatformCallException`
  - `reason="dns"`
  - `isUnreachable() == true`

- [ ] **网络错误（连接拒绝）**：
  - 抛出 `PlatformCallException`
  - `reason="connection_refused"`
  - `isUnreachable() == true`

### ✅ 日志验证

- [ ] **debug 日志**：
  - 记录 `url`、`deviceId`、`activityId`

- [ ] **info 日志**：
  - 成功时记录 `count`

- [ ] **error 日志**：
  - 失败时记录 `reason`、`status`、`url`

---

## 快速验证步骤

### 1. 准备环境

```powershell
# 确保 Platform 服务运行
# 确保 device.json 存在且包含有效的 deviceToken
```

### 2. 运行测试脚本

```powershell
.\test_templates_api_client.ps1
```

### 3. 检查输出

- ✅ 看到 `[OK] Request successful`
- ✅ 看到 `[OK] API returned success=true`
- ✅ 看到 `data is an array with X item(s)`
- ✅ 每个 item 包含 `updatedAt` 字段

### 4. 测试错误场景

```powershell
# 测试 401（使用无效 token）
.\test_templates_api_client.ps1 -deviceToken "invalid_token"

# 测试 404（使用不存在的 activityId）
.\test_templates_api_client.ps1 -activityId 99999

# 测试网络错误（使用错误的 platformUrl）
.\test_templates_api_client.ps1 -platformUrl "http://127.0.0.1:99999"
```

---

## 预期结果

### 成功响应示例

```json
{
  "success": true,
  "data": [
    {
      "templateId": 1,
      "name": "Template Name",
      "coverUrl": "https://...",
      "version": "1.0.0",
      "downloadUrl": "https://...",
      "checksum": "sha256:...",
      "enabled": true,
      "updatedAt": "2026-02-04T12:00:00Z"
    }
  ],
  "message": null
}
```

### 错误响应示例（401）

```json
{
  "success": false,
  "data": null,
  "message": "Invalid or unauthorized device token"
}
```

---

## 注意事项

1. **Token 获取**：测试脚本会自动从 `device.json` 读取 token，如果没有需要先执行 handshake
2. **Platform 服务**：确保 Platform 服务正在运行（默认端口 8089）
3. **数据准备**：确保 Platform 数据库中有对应的 device、activity、template 数据
4. **日志查看**：检查应用日志，确认 API Client 的日志输出符合预期

---

## 下一步

验证通过后，可以：
1. 在 `DevicePlatformController` 中集成此方法
2. 实现 `/local/device/activities/{activityId}/templates` 接口
3. 添加缓存支持（复用 activities 的模式）
