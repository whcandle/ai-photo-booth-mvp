# Templates Endpoint 实现说明

## 新增接口

**GET** `/local/device/activities/{activityId}/templates`

获取指定活动的模板列表，支持在线优先 + 离线缓存回退。

---

## 修改/新增的文件

### 1. `DeviceCacheStore.java`
**新增方法**：
- `writeTemplatesCache(Path dir, Long activityId, List<Map<String, Object>> items)`
- `readTemplatesCache(Path dir, Long activityId): Optional<CachePayload>`

**功能**：
- 缓存文件：`templates_cache_{activityId}.json`
- 原子写：tmp + rename
- 复用 `CachePayload` 类

### 2. `DevicePlatformController.java`
**新增方法**：
- `getActivityTemplates(@PathVariable Long activityId, HttpServletRequest request)`

**功能**：
- 完全对齐 `getActivities()` 的实现模式
- 在线优先，失败回退缓存
- 错误处理与 activities 一致

---

## 响应结构

### 在线成功
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "templateId": 3,
        "name": "test001",
        "coverUrl": null,
        "version": "1",
        "downloadUrl": "testurl",
        "checksum": "1",
        "enabled": true,
        "updatedAt": "2026-01-28T08:04:49Z"
      }
    ],
    "stale": false
  },
  "message": null
}
```

### 缓存回退（平台不可达）
```json
{
  "success": true,
  "data": {
    "items": [...],
    "stale": true,
    "cachedAt": "2026-02-04T12:00:00.123Z"
  },
  "message": "using cached data"
}
```

### 错误响应
```json
{
  "success": false,
  "data": null,
  "message": "error message"
}
```

---

## 缓存文件

### 文件位置
- **文件名**：`templates_cache_{activityId}.json`
- **目录**：与 `device.json` 同目录（运行目录）

### 文件内容
```json
{
  "cachedAt": "2026-02-04T12:00:00.123Z",
  "items": [
    {
      "templateId": 3,
      "name": "test001",
      ...
    }
  ]
}
```

### 原子写
- 临时文件：`templates_cache_{activityId}.json.tmp`
- 使用 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`

---

## 失败回退规则

| 异常类型 | HTTP 状态 | 是否读缓存 | 返回 |
|---------|----------|-----------|------|
| **401 Unauthorized** | 401 | ❌ **不读** | HTTP 401 |
| **503 Unreachable** | 200 | ✅ **读缓存** | 有缓存：200 + `{items, stale:true, cachedAt}`<br>无缓存：503 |
| **其他 HTTP 错误** | 对应状态码 | ❌ **不读** | 对应 HTTP 状态码 |

---

## 验收测试

### 1. 在线拉取（生成缓存）

```powershell
# PowerShell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing

# 或使用测试脚本
.\test_templates_endpoint.ps1 -activityId 8
```

**验证点**：
- ✅ HTTP 200
- ✅ `success=true`
- ✅ `data.stale=false`
- ✅ 缓存文件 `templates_cache_8.json` 已创建

### 2. 离线回退（使用缓存）

**步骤**：
1. 停止 Platform 服务（端口 8089）
2. 再次调用接口

```powershell
# PowerShell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing
```

**验证点**：
- ✅ HTTP 200
- ✅ `success=true`
- ✅ `data.stale=true`
- ✅ `data.cachedAt` 存在
- ✅ `message="using cached data"`

### 3. 无缓存时平台不可达

**步骤**：
1. 删除缓存文件 `templates_cache_8.json`
2. 停止 Platform 服务
3. 调用接口

**验证点**：
- ✅ HTTP 503
- ✅ `success=false`
- ✅ `message="platform unreachable and no cache"`

### 4. 401 错误（不读缓存）

**步骤**：
1. 使用无效 token（或修改 device.json 中的 token）
2. 调用接口

**验证点**：
- ✅ HTTP 401
- ✅ `success=false`
- ✅ 不读取缓存

---

## curl 验收命令

### 在线拉取（生成缓存）
```bash
curl -X GET "http://127.0.0.1:8080/local/device/activities/8/templates"
```

### 验证缓存文件
```bash
# Windows PowerShell
Get-Content templates_cache_8.json | ConvertFrom-Json | ConvertTo-Json

# Linux/Mac
cat templates_cache_8.json | jq .
```

### 离线回退（停止 Platform 后）
```bash
curl -X GET "http://127.0.0.1:8080/local/device/activities/8/templates"
```

**预期**：返回 `stale=true` + `cachedAt`

---

## 快速测试方法

### 方法 1：使用测试脚本
```powershell
# 在线测试
.\test_templates_endpoint.ps1 -activityId 8

# 停止 Platform 后再次运行
.\test_templates_endpoint.ps1 -activityId 8
```

### 方法 2：手动测试（PowerShell）
```powershell
# 1. 在线拉取
$r1 = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing
$r1.Content | ConvertFrom-Json | ConvertTo-Json

# 2. 检查缓存文件
Test-Path "templates_cache_8.json"

# 3. 停止 Platform 后再次调用
$r2 = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing
$r2.Content | ConvertFrom-Json | ConvertTo-Json
# 应该看到 stale=true
```

### 方法 3：一行命令测试
```powershell
# 测试并显示结果
try { $r = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing; Write-Host "Status: $($r.StatusCode)"; $r.Content | ConvertFrom-Json | ConvertTo-Json } catch { Write-Host "Status: $($_.Exception.Response.StatusCode.value__)"; $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $reader.ReadToEnd() | ConvertFrom-Json | ConvertTo-Json }
```

---

## 实现对齐检查

✅ **响应结构**：与 activities 完全一致  
✅ **缓存文件**：命名规则 `templates_cache_{activityId}.json`  
✅ **原子写**：tmp + rename  
✅ **失败回退**：401 不读缓存，503 读缓存  
✅ **配置读取**：从 device.json 读取（DeviceConfigStore 单一真源）  
✅ **localhost 检查**：与 activities 一致  

---

## 注意事项

1. **缓存文件命名**：每个 activityId 有独立的缓存文件
2. **目录位置**：与 `device.json` 同目录（运行目录）
3. **错误处理**：完全对齐 activities 的逻辑
4. **日志输出**：包含 activityId 便于调试
