# Activities 缓存与返回结构现状检查

## 检查时间
2026-02-04

## 相关文件
- `DevicePlatformController.java` - `/local/device/activities` 接口实现
- `PlatformDeviceApiClient.java` (device 包) - 平台 API 客户端
- `DeviceCacheStore.java` - 缓存存储工具类

---

## 1. 返回数据结构

### 成功响应（在线获取）
```json
{
  "success": true,
  "data": {
    "items": [...],      // List<Map<String, Object>> 活动列表
    "stale": false      // 标识数据是否来自缓存
  },
  "message": null
}
```

### 缓存回退响应（离线/平台不可达）
```json
{
  "success": true,
  "data": {
    "items": [...],                    // 缓存的活动列表
    "stale": true,                     // 标识数据来自缓存
    "cachedAt": "2026-02-04T12:00:00Z" // ISO8601 格式的缓存时间（UTC）
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

**关键点**：
- ✅ `data` 是对象，不是直接数组
- ✅ `data.items` 是活动列表
- ✅ `data.stale` 标识是否来自缓存
- ✅ `data.cachedAt` 仅在 `stale=true` 时存在
- ❌ 没有 `source` 字段
- ❌ 没有 `ttl` 字段

---

## 2. 缓存文件结构

### 文件位置
- **文件名**: `activities_cache.json`
- **目录**: 与 `device.json` **同目录**
  - 代码逻辑：`Path dir = file.getParent() != null ? file.getParent() : Path.of(".")`
  - 即：`device.json` 所在目录（通常是运行目录）

### 文件内容结构
```json
{
  "cachedAt": "2026-02-04T12:00:00.123Z",  // Instant (ISO8601 UTC)
  "items": [                                // List<Map<String, Object>>
    {
      "activityId": 1,
      "name": "Activity Name",
      ...
    }
  ]
}
```

**关键点**：
- ✅ 只有 `cachedAt` 和 `items` 两个字段
- ✅ `cachedAt` 是 `Instant` 类型，序列化为 ISO8601 字符串（UTC）
- ❌ 没有 `ttl`、`expiresAt`、`source` 等字段

---

## 3. 原子写机制

### 实现方式
- ✅ **使用 tmp + rename 原子替换**
- 步骤：
  1. 写入临时文件：`activities_cache.json.tmp`
  2. 使用 `Files.move(tmpFile, cacheFile, ATOMIC_MOVE, REPLACE_EXISTING)`
  3. 如果 `ATOMIC_MOVE` 不支持，回退到 `REPLACE_EXISTING`

### 代码位置
`DeviceCacheStore.writeActivitiesCache()` (第 44-87 行)

```java
// 原子写：write to tmp file first, then rename
tmpFile = cacheFile.resolveSibling(cacheFile.getFileName().toString() + ".tmp");
Files.writeString(tmpFile, json, ...);
Files.move(tmpFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
```

---

## 4. 失败回退判定逻辑

### 异常分类（PlatformCallException）

#### 401 Unauthorized（token 无效/过期）
- **行为**: ❌ **不读缓存**，直接返回 HTTP 401
- **原因**: 401 表示认证失败，缓存数据可能已过期，不应使用
- **代码位置**: `DevicePlatformController.getActivities()` 第 179-183 行

```java
if (e.isUnauthorized()) {
  // 401: Token invalid/expired
  return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
      .body(createErrorResponse("token invalid/expired"));
}
```

#### 503 Unreachable（平台不可达）
- **行为**: ✅ **尝试读缓存**
  - 有缓存：返回 HTTP 200 + `{items, stale: true, cachedAt}`
  - 无缓存：返回 HTTP 503
- **触发条件**:
  - `PlatformCallException.isUnreachable() == true`
  - 包括：DNS 失败、连接拒绝、超时、网络不可达等
- **代码位置**: `DevicePlatformController.getActivities()` 第 184-210 行

```java
else if (e.isUnreachable()) {
  // 503: Platform unreachable, try cache
  var cacheOpt = cacheStore.readActivitiesCache(dir);
  if (cacheOpt.isPresent()) {
    // 返回缓存数据，stale=true
  } else {
    // 返回 503
  }
}
```

#### 其他 HTTP 错误（4xx/5xx，非 401/503）
- **行为**: ❌ **不读缓存**，返回对应 HTTP 状态码
- **代码位置**: `DevicePlatformController.getActivities()` 第 211-220 行

### PlatformCallException 的 reason 分类
- `"unauthorized"` → 401，不读缓存
- `"unreachable"` → 503，读缓存
- `"dns"` → 503，读缓存
- `"connection_refused"` → 503，读缓存
- `"timeout"` → 503，读缓存
- `"http_error"` → 其他 HTTP 错误，不读缓存

---

## 5. Templates 复用模式总结

### 需要复用的模式

1. **返回结构**：
   ```json
   {
     "success": true,
     "data": {
       "items": [...],      // templates 列表
       "stale": false,      // 或 true
       "cachedAt": "..."    // 仅 stale=true 时存在
     }
   }
   ```

2. **缓存文件**：
   - 文件名：`templates_cache_{activityId}.json`（建议）
   - 目录：与 `device.json` 同目录
   - 结构：`{cachedAt: Instant, items: List<Map>}`

3. **原子写**：
   - 使用 `DeviceCacheStore` 的相同模式（tmp + rename）

4. **失败回退**：
   - 401 → 不读缓存，返回 401
   - 503/unreachable → 读缓存，有缓存返回 200+stale，无缓存返回 503
   - 其他 HTTP 错误 → 不读缓存，返回对应状态码

### 建议新增方法

在 `DeviceCacheStore` 中新增：
- `writeTemplatesCache(Path dir, Long activityId, List<Map<String, Object>> items)`
- `readTemplatesCache(Path dir, Long activityId): Optional<CachePayload>`

复用现有的 `CachePayload` 类和原子写逻辑。

---

## 检查结论

✅ **当前实现清晰、一致，可直接复用到 templates**

- 返回结构：`data.items` + `data.stale` + `data.cachedAt`（可选）
- 缓存文件：与 `device.json` 同目录，原子写
- 失败回退：401 不读缓存，503 读缓存，其他错误不读缓存

**下一步**：在 `DevicePlatformController` 中新增 `/local/device/activities/{activityId}/templates` 接口，完全复用 activities 的模式。
