# D2 本地代理 API 文档

## 📋 接口概览

Base path: `/local/device`

所有接口仅允许 localhost 访问（只信任 `request.getRemoteAddr()`）。

---

## 1. POST /local/device/handshake

执行平台 handshake 并更新 device.json。

### 请求
```bash
POST http://127.0.0.1:8080/local/device/handshake
```

无需请求体，从 device.json 读取配置。

### 响应

**成功 (200)**：
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

**失败 (200)**：
```json
{
  "success": false,
  "data": null,
  "message": "platformBaseUrl not configured"
}
```

### 前置条件
- device.json 必须存在
- `platformBaseUrl`、`deviceCode`、`secret` 必须配置

### 行为
1. 读取 device.json
2. 校验必填字段
3. 调用平台 handshake API
4. 更新 device.json（原子写）
5. 返回最新配置

---

## 2. GET /local/device/activities

获取活动列表（在线优先，离线回退缓存）。

### 请求
```bash
GET http://127.0.0.1:8080/local/device/activities
```

### 响应

#### 成功 - 在线数据 (200)
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

#### 成功 - 缓存数据 (200)
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

#### 失败 - Token 无效 (401)
```json
{
  "success": false,
  "data": null,
  "message": "token invalid/expired"
}
```

#### 失败 - 平台不可达且无缓存 (503)
```json
{
  "success": false,
  "data": null,
  "message": "platform unreachable and no cache"
}
```

### 前置条件
- device.json 必须存在
- `platformBaseUrl`、`deviceId`、`deviceToken` 必须配置（需要先执行 handshake）

### 行为
1. 读取 device.json
2. 校验必填字段
3. **在线优先**：调用平台 API
   - 成功：写入缓存，返回 `stale=false`
   - 401：返回 HTTP 401
   - 503：尝试读取缓存
4. **离线回退**：如果平台不可达（503）
   - 缓存存在：返回缓存数据，`stale=true`，HTTP 200
   - 缓存不存在：返回 HTTP 503

---

## 📝 curl 示例

### 1. Handshake

```bash
# 执行 handshake
curl -X POST http://127.0.0.1:8080/local/device/handshake

# 预期响应
# {
#   "success": true,
#   "data": { ... },
#   "message": "OK"
# }
```

### 2. Get Activities（在线场景）

```bash
# 获取活动列表（平台在线）
curl http://127.0.0.1:8080/local/device/activities

# 预期响应（stale=false）
# {
#   "success": true,
#   "data": {
#     "items": [ ... ],
#     "stale": false
#   },
#   "message": null
# }
```

### 3. Get Activities（离线场景 - 有缓存）

```bash
# 1. 先在线获取一次（创建缓存）
curl http://127.0.0.1:8080/local/device/activities

# 2. 断开平台连接或停止平台服务

# 3. 再次请求（会使用缓存）
curl http://127.0.0.1:8080/local/device/activities

# 预期响应（stale=true）
# {
#   "success": true,
#   "data": {
#     "items": [ ... ],
#     "stale": true,
#     "cachedAt": "2026-02-04T09:00:00Z"
#   },
#   "message": "using cached data"
# }
```

### 4. Get Activities（离线场景 - 无缓存）

```bash
# 如果从未成功获取过活动列表，且平台不可达
curl http://127.0.0.1:8080/local/device/activities

# 预期响应（HTTP 503）
# {
#   "success": false,
#   "data": null,
#   "message": "platform unreachable and no cache"
# }
```

### 5. Get Activities（Token 过期）

```bash
# 如果 token 过期或无效
curl http://127.0.0.1:8080/local/device/activities

# 预期响应（HTTP 401）
# {
#   "success": false,
#   "data": null,
#   "message": "token invalid/expired"
# }
```

---

## 🔒 安全

- **localhost-only**：只信任 `request.getRemoteAddr()`
- 允许的地址：
  - `127.0.0.1` (IPv4)
  - `::1` (IPv6)
  - `0:0:0:0:0:0:0:1` (IPv6 完整格式)
- **不信任**：Host header、X-Forwarded-For header

---

## 📁 文件结构

### 缓存文件
- **位置**：与 device.json 同目录
- **文件名**：`activities_cache.json`
- **格式**：
```json
{
  "cachedAt": "2026-02-04T09:00:00Z",
  "items": [ ... ]
}
```

### 原子写
- 使用 tmp 文件 + rename 确保原子性
- 失败时自动清理 tmp 文件

---

## ✅ 验收点

- [x] POST /local/device/handshake 正常工作
- [x] GET /local/device/activities 在线优先
- [x] GET /local/device/activities 离线回退缓存
- [x] 401 错误返回 HTTP 401
- [x] 503 错误有缓存返回 200，无缓存返回 503
- [x] localhost-only 安全检查
- [x] 缓存原子写

---

## 🔄 与现有接口的关系

- **DeviceConfigController** (`/local/device/config`)：配置管理
- **DevicePlatformController** (`/local/device/handshake`, `/local/device/activities`)：平台代理
- **DeviceProxyController** (`/api/v1/device/activities`)：旧版代理（可保留兼容）
