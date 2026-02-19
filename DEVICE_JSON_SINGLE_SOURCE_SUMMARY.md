# Device.json Single Source of Truth - 完整工作总结

## 📋 项目概述

将 `device.json` 确立为设备配置的**单一数据源（Single Source of Truth）**，统一所有设备配置的读写入口，确保数据一致性和原子性操作。

---

## 🎯 核心目标

1. **单一写入入口**：所有 `device.json` 的写入操作都通过 `DeviceConfigStore`，确保原子写
2. **数据一致性**：UI 配置和 Bootstrap handshake 不会互相覆盖
3. **向后兼容**：支持旧格式的 `device.json`（Long deviceId → String deviceId）
4. **类型安全**：统一使用 `DeviceConfig` 作为数据模型
5. **原子操作**：所有文件写入使用临时文件 + 原子重命名

---

## 📦 核心组件

### 1. DeviceConfig（数据模型）

**文件**：`src/main/java/com/mg/booth/device/DeviceConfig.java`

**功能**：
- 设备配置数据模型，对应 `device.json` 结构
- 字段：
  - **可写字段**：`platformBaseUrl`, `deviceCode`, `secret`
  - **只读字段**：`deviceId`, `deviceToken`, `tokenExpiresAt`
- 辅助方法：
  - `setDeviceIdFromLong(Long)` / `getDeviceIdAsLong()` - Long ↔ String 转换
  - `setTokenExpiresAtFromInstant(Instant)` / `getTokenExpiresAtAsInstant()` - Instant ↔ ISO8601 String 转换
  - `isTokenValid()` - Token 有效性检查（30 秒缓冲）
  - `@JsonIgnore` 标记辅助方法，防止序列化

**关键特性**：
- 自动处理类型转换（Long deviceId → String）
- ISO8601 时间格式标准化
- Token 过期检查（30 秒缓冲时间）

---

### 2. DeviceConfigStore（单一写入入口）

**文件**：`src/main/java/com/mg/booth/device/DeviceConfigStore.java`

**功能**：
- `device.json` 的**唯一读写入口**
- 原子写操作（临时文件 + 原子重命名）
- 向后兼容（支持旧格式）
- 自动创建默认配置

**方法**：
- `load(Path file)` - 加载配置，文件不存在时创建默认配置
- `save(Path file, DeviceConfig config)` - 原子写配置
- `normalizeConfig(DeviceConfig config)` - 配置规范化（类型转换、格式统一）

**关键特性**：
- **原子写**：使用 `Files.move(tmpFile, targetFile, ATOMIC_MOVE)` 确保写入不中断
- **向后兼容**：自动处理旧格式（Long deviceId、数字字符串 tokenExpiresAt）
- **自动创建**：文件不存在时自动创建默认配置
- **格式规范化**：保存时自动规范化（tokenExpiresAt → ISO8601）

---

### 3. DeviceBootstrapRunner（启动自动同步）

**文件**：`src/main/java/com/mg/booth/device/DeviceBootstrapRunner.java`

**功能**：
- 应用启动时自动执行平台同步
- 读取 `device.json`（使用 `DeviceConfigStore`）
- 如果 token 无效或缺失，自动执行 handshake
- 自动拉取 activities 列表
- 使用 `DeviceConfigStore.save()` 更新配置（原子写）

**关键特性**：
- **非阻塞**：所有异常都被捕获，不会导致启动失败
- **智能判断**：只在 token 无效或缺失时执行 handshake
- **保留可写字段**：只更新只读字段（deviceId/token），保留 UI 配置

---

### 4. DevicePlatformController（本地代理接口）

**文件**：`src/main/java/com/mg/booth/api/DevicePlatformController.java`

**功能**：
- 提供 localhost-only 的代理接口
- Base path: `/local/device`

**接口**：

1. **POST /local/device/handshake**
   - 读取 `device.json`
   - 调用平台 handshake
   - 更新 `device.json`（原子写）
   - 返回最新配置

2. **GET /local/device/activities**
   - 在线优先：调用平台 API
   - 成功：写入缓存，返回 `stale=false`
   - 401：返回 HTTP 401
   - 503：离线回退缓存（`stale=true`）

3. **GET /local/device/activities/{activityId}/templates**
   - 在线优先：调用平台 API
   - 成功：写入缓存，返回 `stale=false`
   - 401：返回 HTTP 401
   - 503：离线回退缓存（`stale=true`）

**关键特性**：
- **localhost-only**：只信任 `127.0.0.1`、`::1`、`0:0:0:0:0:0:0:1`
- **在线优先 + 离线回退**：确保离线场景可用
- **缓存管理**：自动管理缓存文件

---

### 5. DeviceConfigController（配置管理接口）

**文件**：`src/main/java/com/mg/booth/api/DeviceConfigController.java`

**功能**：
- 提供设备配置的读写接口
- Base path: `/local/device/config`

**接口**：

1. **GET /local/device/config**
   - 读取 `device.json`（使用 `DeviceConfigStore.load()`）
   - 返回完整配置

2. **PUT /local/device/config**
   - 更新可写字段（platformBaseUrl, deviceCode, secret）
   - **保留只读字段**（deviceId, deviceToken, tokenExpiresAt）
   - 使用 `DeviceConfigStore.save()` 原子写

**关键特性**：
- **字段保护**：只允许更新可写字段，保护只读字段
- **原子写**：使用 `DeviceConfigStore.save()` 确保一致性

---

### 6. DeviceCacheStore（缓存管理）

**文件**：`src/main/java/com/mg/booth/device/DeviceCacheStore.java`

**功能**：
- Activities 和 Templates 缓存管理
- 原子写操作
- 缓存文件位置：与 `device.json` 同目录

**方法**：
- `writeActivitiesCache(Path dir, List<Map<String, Object>> items)` - 写入 activities 缓存
- `readActivitiesCache(Path dir)` - 读取 activities 缓存
- `writeTemplatesCache(Path dir, Long activityId, List<Map<String, Object>> items)` - 写入 templates 缓存
- `readTemplatesCache(Path dir, Long activityId)` - 读取 templates 缓存

**关键特性**：
- **原子写**：使用临时文件 + 原子重命名
- **CachePayload 结构**：包含 `cachedAt` 和 `items`
- **位置统一**：所有缓存文件与 `device.json` 同目录

---

### 7. PlatformDeviceApiClient（平台 API 客户端）

**文件**：`src/main/java/com/mg/booth/device/PlatformDeviceApiClient.java`

**功能**：
- 平台 API 调用封装
- 统一的异常处理（`PlatformCallException`）
- 错误分类（unauthorized, unreachable, dns, timeout, connection_refused, http_error）

**方法**：
- `handshake(String baseUrl, String deviceCode, String secret)` - 握手
- `listActivities(String baseUrl, Long deviceId, String deviceToken)` - 获取活动列表
- `listActivityTemplates(String baseUrl, Long deviceId, Long activityId, String deviceToken)` - 获取模板列表

**关键特性**：
- **异常分类**：详细的错误类型，便于前端处理
- **重试策略**：支持在线优先 + 离线回退

---

### 8. TemplatePackageInstaller（模板安装器）

**文件**：`src/main/java/com/mg/booth/device/TemplatePackageInstaller.java`

**功能**：
- 模板包下载、校验、解压、原子安装
- 使用 `templateCode` + `versionSemver`（与 manifest 对齐）
- 落盘目录：`data/templates/<templateCode>/<versionSemver>/`

**关键特性**：
- **原子安装**：staging → final 目录的原子移动
- **SHA256 校验**：下载后校验文件完整性
- **Manifest 验证**：校验 `templateId` 和 `version` 与请求一致
- **并发控制**：使用 `ReentrantLock` 防止并发安装冲突

---

### 9. LocalTemplateIndexStore（模板索引管理）

**文件**：`src/main/java/com/mg/booth/device/LocalTemplateIndexStore.java`

**功能**：
- 管理 `index.json` 文件（已安装模板索引）
- Schema Version 2：使用 `templateCode` + `versionSemver`
- 向后兼容：自动迁移 Schema Version 1 → 2

**关键特性**：
- **原子写**：使用临时文件 + 原子重命名
- **版本迁移**：自动处理旧格式迁移
- **索引结构**：`templateId`（存储 templateCode）、`version`（存储 versionSemver）

---

## 🔄 数据流

### 1. 启动流程

```
应用启动
  ↓
DeviceBootstrapRunner.run()
  ↓
DeviceConfigStore.load(device.json)
  ↓
检查 token 有效性
  ↓
[Token 无效] → PlatformDeviceApiClient.handshake()
  ↓
DeviceConfigStore.save(device.json) [原子写]
  ↓
PlatformDeviceApiClient.listActivities()
  ↓
DeviceCacheStore.writeActivitiesCache() [原子写]
```

### 2. UI 配置更新流程

```
UI PUT /local/device/config
  ↓
DeviceConfigController.saveDeviceConfig()
  ↓
读取现有配置（DeviceConfigStore.load()）
  ↓
合并可写字段（保留只读字段）
  ↓
DeviceConfigStore.save(device.json) [原子写]
```

### 3. Handshake 流程

```
UI POST /local/device/handshake
  ↓
DevicePlatformController.handshake()
  ↓
DeviceConfigStore.load(device.json)
  ↓
PlatformDeviceApiClient.handshake()
  ↓
更新配置（只更新只读字段，保留可写字段）
  ↓
DeviceConfigStore.save(device.json) [原子写]
```

### 4. 模板安装流程

```
UI POST /local/device/templates/install
  ↓
TemplateController.install()
  ↓
TemplatePackageInstaller.install(templateCode, versionSemver, ...)
  ↓
下载 → 校验 → 解压 → 验证 manifest
  ↓
原子移动到 final 目录
  ↓
LocalTemplateIndexStore.writeIndex() [原子写]
```

---

## 📊 文件结构

```
ai-photo-booth-mvp/
├── device.json                    # 单一数据源（设备配置）
├── activities_cache.json          # Activities 缓存（与 device.json 同目录）
├── templates_cache_{activityId}.json  # Templates 缓存（与 device.json 同目录）
├── data/
│   ├── templates/                 # 已安装模板
│   │   └── <templateCode>/
│   │       └── <versionSemver>/
│   ├── index.json                 # 模板索引（Schema Version 2）
│   └── tmp/
│       ├── downloads/             # 临时下载目录
│       └── staging/               # 临时解压目录
└── src/main/java/com/mg/booth/
    ├── device/
    │   ├── DeviceConfig.java              # 数据模型
    │   ├── DeviceConfigStore.java         # 单一写入入口
    │   ├── DeviceBootstrapRunner.java     # 启动自动同步
    │   ├── DeviceCacheStore.java          # 缓存管理
    │   ├── PlatformDeviceApiClient.java   # 平台 API 客户端
    │   ├── TemplatePackageInstaller.java  # 模板安装器
    │   └── LocalTemplateIndexStore.java   # 模板索引管理
    └── api/
        ├── DeviceConfigController.java     # 配置管理接口
        └── DevicePlatformController.java   # 平台代理接口
```

---

## 🔐 安全特性

### 1. localhost-only 接口

所有 `/local/device/*` 接口只允许 localhost 访问：
- `127.0.0.1`
- `::1`
- `0:0:0:0:0:0:0:1`

### 2. 原子写操作

所有文件写入都使用原子操作：
```java
// 临时文件
Path tmpFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");
Files.writeString(tmpFile, content);

// 原子移动
Files.move(tmpFile, targetFile, ATOMIC_MOVE, REPLACE_EXISTING);
```

### 3. 字段保护

- **只读字段**：`deviceId`, `deviceToken`, `tokenExpiresAt`（只能通过 handshake 更新）
- **可写字段**：`platformBaseUrl`, `deviceCode`, `secret`（可通过 UI 更新）

---

## 🔄 向后兼容

### 1. 旧格式 device.json

支持读取旧格式：
- `deviceId` 为 Long 类型 → 自动转换为 String
- `tokenExpiresAt` 为数字字符串 → 自动转换为 ISO8601

### 2. 旧格式 index.json

支持 Schema Version 1 → 2 自动迁移：
- `templateId` 从 numeric ID → templateCode（String）
- `version` 保持 String（现在明确为 versionSemver）

---

## 📈 演进历程

### Phase 1: 初始实现
- `DeviceIdentity` + `DeviceIdentityStore`（基础读写）

### Phase 2: 单一真源重构
- `DeviceConfig` + `DeviceConfigStore`（统一入口）
- 禁用 `DeviceIdentityStore.save()`（防止冲突）
- 原子写操作

### Phase 3: 平台集成
- `DeviceBootstrapRunner`（启动自动同步）
- `PlatformDeviceApiClient`（统一 API 客户端）
- `DevicePlatformController`（本地代理接口）

### Phase 4: 缓存管理
- `DeviceCacheStore`（Activities/Templates 缓存）
- 在线优先 + 离线回退策略

### Phase 5: 模板管理
- `TemplatePackageInstaller`（模板安装器）
- `LocalTemplateIndexStore`（模板索引）
- 从 numeric ID 迁移到 `templateCode` + `versionSemver`

### Phase 6: UI 更新
- Kiosk UI 支持新字段（`templateCode`/`versionSemver`）
- 兼容旧字段（fallback 机制）
- Install API 使用新字段

---

## ✅ 验收标准

### 1. 数据一致性
- [x] UI 配置和 Bootstrap handshake 不会互相覆盖
- [x] 只读字段只能通过 handshake 更新
- [x] 可写字段只能通过 UI 更新

### 2. 原子性
- [x] 所有文件写入使用原子操作
- [x] 并发写入不会导致数据损坏

### 3. 向后兼容
- [x] 支持读取旧格式 `device.json`
- [x] 支持 Schema Version 1 → 2 迁移

### 4. 功能完整性
- [x] 启动自动同步
- [x] UI 配置管理
- [x] 平台代理接口
- [x] 离线回退缓存
- [x] 模板安装管理

---

## 📝 关键文档

1. **DEVICE_CONFIG_MIGRATION_TEST.md** - 迁移测试清单
2. **DEVICE_BOOTSTRAP_README.md** - 启动自动同步说明
3. **D2_LOCAL_PROXY_API.md** - 本地代理接口文档
4. **D2_DELIVERY_SUMMARY.md** - D2 阶段交付清单
5. **TEMPLATE_INSTALL_API_V2.md** - 模板安装 API v2 文档
6. **DEVICE_API_CLIENT_README.md** - 平台 API 客户端说明

---

## 🎯 核心原则

1. **单一写入入口**：所有 `device.json` 写入都通过 `DeviceConfigStore`
2. **原子操作**：所有文件写入使用临时文件 + 原子重命名
3. **字段保护**：只读字段和可写字段分离，防止误覆盖
4. **向后兼容**：支持旧格式自动迁移
5. **非阻塞设计**：启动同步失败不会导致应用启动失败
6. **在线优先 + 离线回退**：确保离线场景可用

---

## 📊 统计数据

- **核心类数量**：9 个
- **API 接口数量**：6 个
- **文档数量**：10+ 个
- **测试脚本数量**：5+ 个
- **代码行数**：~3000+ 行

---

## 🚀 后续优化方向

1. **配置验证**：添加配置字段验证规则
2. **配置版本化**：支持配置版本迁移
3. **配置加密**：敏感字段（secret）加密存储
4. **配置备份**：自动备份配置变更
5. **配置同步**：多设备配置同步

---

## 📅 完成时间线

- **Phase 1-2**：单一真源重构（DeviceConfig + DeviceConfigStore）
- **Phase 3**：平台集成（DeviceBootstrapRunner + PlatformDeviceApiClient）
- **Phase 4**：缓存管理（DeviceCacheStore）
- **Phase 5**：模板管理（TemplatePackageInstaller + LocalTemplateIndexStore）
- **Phase 6**：UI 更新（Kiosk UI 支持新字段）

**总计交互轮次**：多轮迭代，逐步完善

---

## 🎉 成果总结

通过建立 `device.json` 作为单一数据源，实现了：

1. ✅ **数据一致性**：所有配置读写统一入口，避免数据冲突
2. ✅ **原子操作**：所有文件写入使用原子操作，确保数据完整性
3. ✅ **向后兼容**：支持旧格式自动迁移，平滑升级
4. ✅ **功能完整**：覆盖启动同步、UI 配置、平台代理、缓存管理、模板安装等所有场景
5. ✅ **类型安全**：统一使用 `DeviceConfig` 数据模型，类型转换自动化
6. ✅ **离线支持**：在线优先 + 离线回退策略，确保离线场景可用

**这是一个完整的、生产级的设备配置管理系统！** 🎊
