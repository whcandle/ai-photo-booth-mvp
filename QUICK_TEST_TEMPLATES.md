# 快速手动测试 listActivityTemplates

## 方法 1：使用 curl（最简单）

### 步骤 1：获取 token
```bash
# 执行 handshake 获取 token（或从 device.json 读取）
curl -X POST http://127.0.0.1:8080/local/device/handshake
```

### 步骤 2：测试 templates API
```bash
# 替换 YOUR_TOKEN 和 deviceId、activityId
curl -X GET \
  "http://127.0.0.1:8089/api/v1/device/4/activities/1/templates" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json"
```

### 步骤 3：查看响应
- ✅ **200 + success=true** → API Client 应该返回 `List<Map>`
- ✅ **401** → API Client 应该抛出 `PlatformCallException`，`reason="unauthorized"`
- ✅ **403** → API Client 应该抛出 `PlatformCallException`，`reason="http_error"`
- ✅ **503** → API Client 应该抛出 `PlatformCallException`，`reason="unreachable"`

---

## 方法 2：使用 PowerShell（一行命令）

### 获取 token 并测试
```powershell
# 1. 执行 handshake
$handshake = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/handshake" -Method POST -UseBasicParsing
$config = $handshake.Content | ConvertFrom-Json

# 2. 测试 templates API（使用返回的 deviceId 和 token）
$deviceId = $config.data.deviceId
$token = $config.data.deviceToken
Invoke-WebRequest -Uri "http://127.0.0.1:8089/api/v1/device/$deviceId/activities/1/templates" -Headers @{Authorization="Bearer $token"} -UseBasicParsing
```

### 或者从 device.json 读取
```powershell
# 读取 device.json
$config = Get-Content device.json | ConvertFrom-Json

# 测试
Invoke-WebRequest -Uri "http://127.0.0.1:8089/api/v1/device/$($config.deviceId)/activities/1/templates" -Headers @{Authorization="Bearer $($config.deviceToken)"} -UseBasicParsing
```

---

## 方法 3：直接在浏览器测试（需要插件）

使用浏览器插件（如 REST Client）或 Postman：

```
GET http://127.0.0.1:8089/api/v1/device/4/activities/1/templates
Authorization: Bearer YOUR_TOKEN
```

---

## 验证点

### ✅ 成功响应
```json
{
  "success": true,
  "data": [
    {
      "templateId": 1,
      "name": "...",
      "updatedAt": "2026-02-04T12:00:00Z",
      ...
    }
  ]
}
```
→ API Client 应该返回 `List<Map<String, Object>>`

### ✅ 错误响应（401）
```json
{
  "success": false,
  "message": "Invalid or unauthorized device token"
}
```
→ API Client 应该抛出 `PlatformCallException.isUnauthorized() == true`

### ✅ 错误响应（403）
```json
{
  "success": false,
  "message": "Device does not have access to this activity"
}
```
→ API Client 应该抛出 `PlatformCallException`，`reason="http_error"`

---

## 最简单的验证流程

1. **执行 handshake**：
   ```powershell
   Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/handshake" -Method POST -UseBasicParsing
   ```

2. **复制返回的 deviceToken**

3. **测试 templates API**：
   ```powershell
   $token = "粘贴你的token"
   Invoke-WebRequest -Uri "http://127.0.0.1:8089/api/v1/device/4/activities/1/templates" -Headers @{Authorization="Bearer $token"} -UseBasicParsing
   ```

4. **查看响应**：
   - 200 + JSON → ✅ 成功
   - 401/403/503 → ✅ 错误处理正确

---

## 检查应用日志

启动应用后，查看日志输出：

- ✅ **成功**：`[device-api] List activity templates success: count=X`
- ✅ **失败**：`[device-api] List activity templates failed: url=..., status=..., reason=...`
