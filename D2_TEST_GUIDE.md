# D2 本地代理接口 - 完整测试指南

## 📋 测试前准备

### 1. 检查 device.json

确保 device.json 存在且配置正确：

```bash
# 查看当前配置
cat device.json
# 或
Get-Content device.json
```

**必需字段**：
- `platformBaseUrl` - 平台地址（如：http://127.0.0.1:8089）
- `deviceCode` - 设备编码
- `secret` - 设备密钥

**可选字段**（handshake 后会自动填充）：
- `deviceId`
- `deviceToken`
- `tokenExpiresAt`

---

## 🚀 测试步骤

### 步骤 1：启动应用

```bash
cd D:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

**等待应用启动完成**，看到：
```
Started AiPhotoBoothApplication in X.XXX seconds
```

---

### 步骤 2：测试 POST /local/device/handshake

#### 2.1 准备测试数据

确保 device.json 包含有效的平台配置：

```bash
# PowerShell
$body = @{
    platformBaseUrl = "http://127.0.0.1:8089"
    deviceCode = "dev_001"
    secret = "your_secret_here"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/config" `
    -Method PUT -ContentType "application/json" -Body $body -UseBasicParsing
```

#### 2.2 执行 handshake

```bash
# PowerShell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/handshake" `
    -Method POST -UseBasicParsing | Select-Object -ExpandProperty Content
```

**预期响应**：
```json
{
  "success": true,
  "data": {
    "platformBaseUrl": "http://127.0.0.1:8089",
    "deviceCode": "dev_001",
    "secret": "***",
    "deviceId": "4",
    "deviceToken": "eyJhbGciOiJIUzM4NCJ9...",
    "tokenExpiresAt": "2026-02-04T12:05:25.580518007Z"
  },
  "message": "OK"
}
```

**验证**：
- ✅ `success: true`
- ✅ `deviceId` 不为空
- ✅ `deviceToken` 不为空
- ✅ `tokenExpiresAt` 是 ISO8601 格式

#### 2.3 验证 device.json 已更新

```bash
# PowerShell
Get-Content device.json | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

**检查**：
- ✅ `deviceId` 已更新
- ✅ `deviceToken` 已更新
- ✅ `tokenExpiresAt` 已更新

---

### 步骤 3：测试 GET /local/device/activities（在线场景）

#### 3.1 确保平台服务运行

确保平台服务在 `http://127.0.0.1:8089` 运行。

#### 3.2 获取活动列表

```bash
# PowerShell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities" `
    -Method GET -UseBasicParsing | Select-Object -ExpandProperty Content
```

**预期响应（在线成功）**：
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "activityId": 1,
        "name": "活动1",
        "status": "active",
        "startAt": "2026-02-01T00:00:00Z",
        "endAt": "2026-02-28T23:59:59Z"
      }
    ],
    "stale": false
  },
  "message": null
}
```

**验证**：
- ✅ `success: true`
- ✅ `stale: false`（表示在线数据）
- ✅ `items` 数组包含活动数据

#### 3.3 验证缓存已创建

```bash
# PowerShell
# 检查缓存文件是否存在
Test-Path activities_cache.json

# 查看缓存内容
Get-Content activities_cache.json | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

**检查**：
- ✅ `activities_cache.json` 文件存在
- ✅ 包含 `cachedAt` 时间戳
- ✅ 包含 `items` 数组

---

### 步骤 4：测试 GET /local/device/activities（离线回退场景）

#### 4.1 停止平台服务

停止平台服务（或修改 platformBaseUrl 为不存在的地址）。

#### 4.2 修改 platformBaseUrl 为无效地址（模拟离线）

```bash
# PowerShell
$body = @{
    platformBaseUrl = "http://invalid.example.com:8089"
    deviceCode = "dev_001"
    secret = "your_secret_here"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/config" `
    -Method PUT -ContentType "application/json" -Body $body -UseBasicParsing
```

**注意**：只更新 `platformBaseUrl`，保留 `deviceId` 和 `deviceToken`。

#### 4.3 获取活动列表（应该使用缓存）

```bash
# PowerShell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities" `
    -Method GET -UseBasicParsing | Select-Object -ExpandProperty Content
```

**预期响应（离线回退）**：
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "activityId": 1,
        "name": "活动1",
        "status": "active"
      }
    ],
    "stale": true,
    "cachedAt": "2026-02-04T09:00:00Z"
  },
  "message": "using cached data"
}
```

**验证**：
- ✅ `success: true`
- ✅ `stale: true`（表示缓存数据）
- ✅ `cachedAt` 存在
- ✅ `message: "using cached data"`

#### 4.4 测试无缓存场景

```bash
# PowerShell
# 删除缓存文件
Remove-Item activities_cache.json -ErrorAction SilentlyContinue

# 再次请求（应该返回 503）
$response = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities" `
    -Method GET -UseBasicParsing -ErrorAction SilentlyContinue

# 检查状态码
$response.StatusCode
# 应该返回 503

# 查看响应
$response.Content
```

**预期响应（无缓存）**：
```json
{
  "success": false,
  "data": null,
  "message": "platform unreachable and no cache"
}
```

**验证**：
- ✅ HTTP 状态码：503
- ✅ `success: false`
- ✅ `message: "platform unreachable and no cache"`

---

### 步骤 5：测试错误场景

#### 5.1 测试 401（Token 过期）

如果平台返回 401，应该返回 HTTP 401：

```bash
# PowerShell
# 修改 deviceToken 为无效值
$config = Get-Content device.json | ConvertFrom-Json
$config.deviceToken = "invalid_token"
$config | ConvertTo-Json -Depth 10 | Set-Content device.json

# 请求 activities
try {
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/activities" `
        -Method GET -UseBasicParsing -ErrorAction Stop
} catch {
    $_.Exception.Response.StatusCode
    # 应该返回 401
}
```

**预期**：
- ✅ HTTP 状态码：401
- ✅ 响应：`{"success": false, "message": "token invalid/expired"}`

#### 5.2 测试配置缺失

```bash
# PowerShell
# 清空 deviceCode
$body = @{
    platformBaseUrl = "http://127.0.0.1:8089"
    deviceCode = ""
    secret = ""
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/config" `
    -Method PUT -ContentType "application/json" -Body $body -UseBasicParsing

# 尝试 handshake
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/handshake" `
    -Method POST -UseBasicParsing | Select-Object -ExpandProperty Content
```

**预期响应**：
```json
{
  "success": false,
  "data": null,
  "message": "deviceCode not configured"
}
```

---

## 📊 完整测试脚本（PowerShell）

创建 `test_d2_api.ps1`：

```powershell
# D2 本地代理接口 - 完整测试脚本

$baseUrl = "http://127.0.0.1:8080"
$platformUrl = "http://127.0.0.1:8089"

Write-Host "=== D2 本地代理接口测试 ===" -ForegroundColor Cyan
Write-Host ""

# 1. 测试 Handshake
Write-Host "1. 测试 POST /local/device/handshake..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/handshake" `
        -Method POST -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        Write-Host "   [OK] Handshake 成功" -ForegroundColor Green
        Write-Host "   deviceId: $($result.data.deviceId)" -ForegroundColor Gray
        Write-Host "   tokenExpiresAt: $($result.data.tokenExpiresAt)" -ForegroundColor Gray
    } else {
        Write-Host "   [ERROR] Handshake 失败: $($result.message)" -ForegroundColor Red
    }
} catch {
    Write-Host "   [ERROR] Handshake 请求失败: $_" -ForegroundColor Red
}

Write-Host ""

# 2. 测试 Activities（在线）
Write-Host "2. 测试 GET /local/device/activities（在线）..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/activities" `
        -Method GET -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        $stale = $result.data.stale
        $count = $result.data.items.Count
        if ($stale) {
            Write-Host "   [WARN] 使用缓存数据 (stale=true)" -ForegroundColor Yellow
            Write-Host "   cachedAt: $($result.data.cachedAt)" -ForegroundColor Gray
        } else {
            Write-Host "   [OK] 在线数据获取成功 (stale=false)" -ForegroundColor Green
        }
        Write-Host "   items count: $count" -ForegroundColor Gray
    } else {
        Write-Host "   [ERROR] Activities 获取失败: $($result.message)" -ForegroundColor Red
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 503) {
        Write-Host "   [WARN] 平台不可达 (503)" -ForegroundColor Yellow
    } elseif ($statusCode -eq 401) {
        Write-Host "   [ERROR] Token 无效 (401)" -ForegroundColor Red
    } else {
        Write-Host "   [ERROR] 请求失败: $_" -ForegroundColor Red
    }
}

Write-Host ""

# 3. 测试 Activities（离线回退）
Write-Host "3. 测试 GET /local/device/activities（离线回退）..." -ForegroundColor Yellow
Write-Host "   提示：请先停止平台服务或修改 platformBaseUrl 为无效地址" -ForegroundColor Gray
Write-Host "   然后按 Enter 继续..." -ForegroundColor Gray
Read-Host

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/activities" `
        -Method GET -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        if ($result.data.stale) {
            Write-Host "   [OK] 离线回退成功，使用缓存 (stale=true)" -ForegroundColor Green
            Write-Host "   cachedAt: $($result.data.cachedAt)" -ForegroundColor Gray
            Write-Host "   items count: $($result.data.items.Count)" -ForegroundColor Gray
        } else {
            Write-Host "   [INFO] 在线数据 (stale=false)" -ForegroundColor Cyan
        }
    } else {
        Write-Host "   [ERROR] Activities 获取失败: $($result.message)" -ForegroundColor Red
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 503) {
        Write-Host "   [WARN] 平台不可达且无缓存 (503)" -ForegroundColor Yellow
    } else {
        Write-Host "   [ERROR] 请求失败: $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== 测试完成 ===" -ForegroundColor Cyan
```

---

## ✅ 验收检查清单

### Handshake 接口
- [ ] 成功执行 handshake
- [ ] device.json 已更新（deviceId/token/tokenExpiresAt）
- [ ] 返回最新配置
- [ ] 配置缺失时返回错误

### Activities 接口 - 在线
- [ ] 成功获取活动列表
- [ ] 返回 `stale=false`
- [ ] 缓存文件已创建
- [ ] 缓存内容正确

### Activities 接口 - 离线
- [ ] 平台不可达时使用缓存
- [ ] 返回 `stale=true` 和 `cachedAt`
- [ ] 无缓存时返回 503

### 错误处理
- [ ] 401 返回 HTTP 401
- [ ] 503 有缓存返回 200，无缓存返回 503
- [ ] 配置缺失返回错误消息

### 安全
- [ ] localhost-only 检查生效
- [ ] 非 localhost 请求返回 403

---

## 🔍 调试技巧

### 查看日志
应用日志会显示：
- `[device-platform] Handshake successful`
- `[device-platform] Activities fetched successfully`
- `[device-platform] Using cached activities`
- `[cache] Activities cache saved`

### 检查文件
```bash
# 检查 device.json
Get-Content device.json

# 检查缓存文件
Get-Content activities_cache.json
```

### 常见问题

1. **handshake 失败**
   - 检查 platformBaseUrl 是否正确
   - 检查平台服务是否运行
   - 检查 deviceCode/secret 是否正确

2. **activities 返回 503**
   - 检查是否先执行过 handshake
   - 检查 platformBaseUrl 是否正确
   - 检查平台服务是否运行

3. **离线回退不工作**
   - 检查是否有缓存文件
   - 检查缓存文件格式是否正确
