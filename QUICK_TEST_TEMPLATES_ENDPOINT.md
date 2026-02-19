# 快速测试 Templates Endpoint

## 最简单的测试方法

### 1. 在线拉取（生成缓存）

```powershell
# 一行命令
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing | Select-Object StatusCode, @{Name='Content';Expression={$_.Content | ConvertFrom-Json | ConvertTo-Json}}
```

**或使用测试脚本**：
```powershell
.\test_templates_endpoint.ps1 -activityId 8
```

**验证**：
- ✅ HTTP 200
- ✅ `stale=false`
- ✅ 缓存文件 `templates_cache_8.json` 已创建

---

### 2. 离线回退（使用缓存）

**步骤**：
1. 停止 Platform 服务（端口 8089）
2. 再次调用：

```powershell
# 一行命令
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing | Select-Object StatusCode, @{Name='Content';Expression={$_.Content | ConvertFrom-Json | ConvertTo-Json}}
```

**验证**：
- ✅ HTTP 200
- ✅ `stale=true`
- ✅ `cachedAt` 字段存在

---

## curl 命令（如果使用 curl）

### 在线拉取
```bash
curl -X GET "http://127.0.0.1:8080/local/device/activities/8/templates"
```

### 离线回退（停止 Platform 后）
```bash
curl -X GET "http://127.0.0.1:8080/local/device/activities/8/templates"
```

---

## 预期响应

### 在线成功
```json
{
  "success": true,
  "data": {
    "items": [...],
    "stale": false
  },
  "message": null
}
```

### 缓存回退
```json
{
  "success": true,
  "data": {
    "items": [...],
    "stale": true,
    "cachedAt": "2026-02-06T01:15:42.820Z"
  },
  "message": "using cached data"
}
```

---

## 完整测试流程

```powershell
# 1. 在线拉取（生成缓存）
$r1 = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing
Write-Host "Online: $($r1.StatusCode)"; $r1.Content | ConvertFrom-Json | ConvertTo-Json

# 2. 检查缓存文件
Test-Path "templates_cache_8.json"

# 3. 停止 Platform 服务（手动操作）

# 4. 离线回退测试
$r2 = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities/8/templates" -UseBasicParsing
Write-Host "Offline: $($r2.StatusCode)"; $r2.Content | ConvertFrom-Json | ConvertTo-Json
# 应该看到 stale=true
```
