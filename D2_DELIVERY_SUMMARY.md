# D2 本地代理接口 - 交付清单

## 📦 新增文件

### 1. DeviceCacheStore.java
**路径**：`src/main/java/com/mg/booth/device/DeviceCacheStore.java`

**功能**：
- Activities 缓存读写
- 原子写（tmp + rename）
- CachePayload 内部类（cachedAt + items）

**方法**：
- `writeActivitiesCache(Path dir, List<Map<String, Object>> items)` - 写入缓存
- `readActivitiesCache(Path dir)` - 读取缓存，返回 `Optional<CachePayload>`

---

### 2. DevicePlatformController.java
**路径**：`src/main/java/com/mg/booth/api/DevicePlatformController.java`

**功能**：
- 本地代理接口（localhost-only）
- Base path: `/local/device`

**接口**：
1. **POST /local/device/handshake**
   - 读取 device.json
   - 调用平台 handshake
   - 更新 device.json（原子写）
   - 返回最新配置

2. **GET /local/device/activities**
   - 在线优先：调用平台 API
   - 成功：写入缓存，返回 `stale=false`
   - 401：返回 HTTP 401
   - 503：离线回退缓存
     - 有缓存：返回 HTTP 200，`stale=true`
     - 无缓存：返回 HTTP 503

**安全**：
- localhost-only：只信任 `request.getRemoteAddr()`
- 允许：`127.0.0.1`, `::1`, `0:0:0:0:0:0:0:1`

---

## 📝 文档文件

### 3. D2_LOCAL_PROXY_API.md
**路径**：`D2_LOCAL_PROXY_API.md`

**内容**：
- API 文档
- curl 示例
- 响应格式说明
- 验收点

---

## 🔄 修改的文件

无（所有功能都是新增的）

---

## ✅ 功能验收

### Handshake 接口
```bash
curl -X POST http://127.0.0.1:8080/local/device/handshake
```

**预期**：
- ✅ 读取 device.json
- ✅ 校验必填字段
- ✅ 调用平台 handshake
- ✅ 更新 device.json（原子写）
- ✅ 返回最新配置

### Activities 接口 - 在线场景
```bash
curl http://127.0.0.1:8080/local/device/activities
```

**预期**：
- ✅ 调用平台 API
- ✅ 成功：写入缓存，返回 `stale=false`
- ✅ 401：返回 HTTP 401
- ✅ 503：尝试读取缓存

### Activities 接口 - 离线场景
```bash
# 1. 先在线获取一次（创建缓存）
curl http://127.0.0.1:8080/local/device/activities

# 2. 断开平台连接

# 3. 再次请求（使用缓存）
curl http://127.0.0.1:8080/local/device/activities
```

**预期**：
- ✅ 有缓存：返回 HTTP 200，`stale=true`，`cachedAt`
- ✅ 无缓存：返回 HTTP 503

---

## 🔒 安全验证

### localhost-only 检查
```bash
# 应该成功（localhost）
curl -X POST http://127.0.0.1:8080/local/device/handshake

# 应该失败（非 localhost，如果从其他机器访问）
# 返回 403 Forbidden
```

---

## 📊 文件清单

```
新增文件：
├── src/main/java/com/mg/booth/device/DeviceCacheStore.java
├── src/main/java/com/mg/booth/api/DevicePlatformController.java
└── D2_LOCAL_PROXY_API.md

修改文件：
（无）

依赖关系：
├── DeviceConfigStore（已存在）
├── PlatformDeviceApiClient（已存在，已支持 PlatformCallException）
└── BoothProps（已存在）
```

---

## 🎯 完成状态

- [x] DeviceCacheStore 实现
- [x] DevicePlatformController 实现
- [x] POST /local/device/handshake
- [x] GET /local/device/activities（在线优先 + 离线回退）
- [x] localhost-only 安全检查
- [x] 异常处理（401/503）
- [x] 缓存原子写
- [x] 文档和 curl 示例
- [x] 编译通过

---

## 🚀 下一步

可以开始测试：
1. 启动应用
2. 执行 handshake
3. 获取 activities（在线）
4. 断开平台，再次获取（离线回退）
