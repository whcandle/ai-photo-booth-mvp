# 平台相关类体系分析

## 📋 类清单

| 类名 | 包路径 | 状态 | 优先级 |
|------|--------|------|--------|
| **DeviceConfigController** | `com.mg.booth.api` | ✅ **核心** | ⭐⭐⭐ |
| **DevicePlatformController** | `com.mg.booth.api` | ✅ **核心（D2）** | ⭐⭐⭐ |
| **DeviceProxyController** | `com.mg.booth.api` | ⚠️ **废弃** | ⭐ |
| **TemplateController** | `com.mg.booth.api` | ✅ **使用中** | ⭐⭐ |
| **PlatformCallException** | `com.mg.booth.device` | ✅ **核心** | ⭐⭐⭐ |
| **PlatformDeviceApiClient** (device) | `com.mg.booth.device` | ✅ **核心** | ⭐⭐⭐ |
| **PlatformDeviceApiClient** (platform) | `com.mg.booth.platform` | ⚠️ **废弃** | ⭐ |
| **PlatformConfigController** | `com.mg.booth.platform` | ⚠️ **废弃** | ⭐ |
| **PlatformSyncService** | `com.mg.booth.platform` | ⚠️ **已禁用** | ⭐ |
| **TemplateService** | `com.mg.booth.service` | ✅ **使用中** | ⭐⭐ |

---

## 🏗️ 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP API 层 (Controller)                  │
├─────────────────────────────────────────────────────────────┤
│ DeviceConfigController      │ 设备配置管理（读写 device.json）│
│ DevicePlatformController    │ D2 平台代理（handshake/activities）│
│ DeviceProxyController       │ ❌ 旧版代理（可废弃）          │
│ TemplateController          │ 模板列表接口                   │
│ PlatformConfigController    │ ❌ 旧版平台配置（可废弃）      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Service 层                                │
├─────────────────────────────────────────────────────────────┤
│ PlatformSyncService          │ ❌ 已禁用（功能迁移）         │
│ TemplateService              │ 模板服务（硬编码列表）         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    API Client 层                             │
├─────────────────────────────────────────────────────────────┤
│ PlatformDeviceApiClient      │ ✅ 新版（device包，带异常处理）│
│ PlatformDeviceApiClient      │ ❌ 旧版（platform包，可废弃） │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    异常处理层                                 │
├─────────────────────────────────────────────────────────────┤
│ PlatformCallException        │ ✅ 平台调用异常（区分错误类型）│
└─────────────────────────────────────────────────────────────┘
```

---

## 📚 详细分析

### 1. ✅ **DeviceConfigController** - 设备配置管理

**路径**: `/local/device/config`  
**作用**: 提供设备配置文件的读写接口

**功能**:
- `GET /local/device/config` - 读取 device.json
- `PUT /local/device/config` - 更新 device.json（仅可写字段）

**特点**:
- ✅ localhost-only 安全限制
- ✅ 字段保护（只允许修改 platformBaseUrl/deviceCode/secret）
- ✅ 使用 DeviceConfigStore（单一真源）

**使用场景**:
- Kiosk Settings 页面查看/修改设备配置
- 设备初始化配置

**状态**: ✅ **核心类，必须保留**

---

### 2. ✅ **DevicePlatformController** - D2 平台代理（核心）

**路径**: `/local/device/*`  
**作用**: 提供平台 API 的本地代理接口（D2 阶段新增）

**功能**:
- `POST /local/device/handshake` - 与平台握手，更新 device.json
- `GET /local/device/activities` - 获取活动列表（在线优先 + 离线缓存）

**特点**:
- ✅ localhost-only 安全限制
- ✅ 离线缓存支持（activities_cache.json）
- ✅ 完善的错误处理（401/503 区分）
- ✅ 使用 PlatformCallException 进行异常分类

**使用场景**:
- Kiosk D2 UI：Handshake 按钮
- Kiosk D2 UI：Fetch Activities 按钮
- 离线场景下的活动列表回退

**状态**: ✅ **核心类（D2），必须保留**

---

### 3. ⚠️ **DeviceProxyController** - 旧版平台代理（可废弃）

**路径**: `/api/v1/device/activities`  
**作用**: 旧版平台代理接口

**功能**:
- `GET /api/v1/device/activities` - 获取活动列表（无缓存）

**问题**:
- ❌ 无离线缓存支持
- ❌ 无 localhost 安全限制
- ❌ 错误处理简单
- ❌ 功能与 DevicePlatformController 重复

**使用情况**:
- 仅测试脚本中使用（`quick_test_device_config.ps1`）
- Kiosk 前端已迁移到 `/local/device/activities`

**建议**: ⚠️ **标记为 @Deprecated，后续可删除**

---

### 4. ✅ **TemplateController** - 模板列表接口

**路径**: `/api/v1/templates`  
**作用**: 提供模板列表查询接口

**功能**:
- `GET /api/v1/templates` - 返回模板列表

**特点**:
- ✅ 使用 TemplateService（硬编码模板）
- ✅ 简单直接，无平台依赖

**使用场景**:
- Kiosk 前端获取可用模板列表
- 业务逻辑选择模板

**状态**: ✅ **使用中，保留**

---

### 5. ✅ **PlatformCallException** - 平台调用异常

**作用**: 自定义异常类，用于区分不同类型的平台调用错误

**字段**:
- `httpStatus` - HTTP 状态码
- `url` - 请求 URL
- `reason` - 错误原因（unauthorized/timeout/dns/connection_refused/http_error/unreachable）
- `responseBody` - 响应体

**方法**:
- `isUnauthorized()` - 检查是否是 401
- `isUnreachable()` - 检查是否是 503

**使用场景**:
- PlatformDeviceApiClient 抛出异常
- DevicePlatformController 根据异常类型返回不同 HTTP 状态码

**状态**: ✅ **核心类，必须保留**

---

### 6. ✅ **PlatformDeviceApiClient** (device包) - 新版平台 API 客户端

**包**: `com.mg.booth.device`  
**Bean 名称**: `devicePlatformDeviceApiClient`

**功能**:
- `handshake()` - 执行平台握手
- `listActivities()` - 获取活动列表

**特点**:
- ✅ 使用 PlatformCallException 进行异常分类
- ✅ 完善的错误处理（401/503/DNS/超时等）
- ✅ 使用 RestTemplate
- ✅ 被 DevicePlatformController 和 DeviceProxyController 使用

**使用场景**:
- DevicePlatformController 调用
- DeviceProxyController 调用（旧版）
- DeviceBootstrapRunner 调用（启动时握手）

**状态**: ✅ **核心类，必须保留**

---

### 7. ⚠️ **PlatformDeviceApiClient** (platform包) - 旧版平台 API 客户端

**包**: `com.mg.booth.platform`  
**Bean 名称**: 无（默认）

**功能**:
- `handshake()` - 执行平台握手
- `listActivities()` - 获取活动列表

**问题**:
- ❌ 使用旧版 RestClient
- ❌ 异常处理简单（只抛出 HttpClientErrorException）
- ❌ 仅被 PlatformSyncService 使用（已禁用）

**使用情况**:
- 仅 PlatformSyncService 使用（已禁用）

**建议**: ⚠️ **可删除（PlatformSyncService 已禁用）**

---

### 8. ⚠️ **PlatformConfigController** - 旧版平台配置接口

**路径**: `/api/v1/platform/activities`  
**作用**: 提供平台活动列表的缓存接口

**功能**:
- `GET /api/v1/platform/activities` - 返回 PlatformSyncService 的缓存活动

**问题**:
- ❌ 依赖 PlatformSyncService（已禁用）
- ❌ 功能与 DevicePlatformController 重复
- ❌ 无使用场景

**使用情况**:
- 无前端调用
- PlatformSyncService 已禁用

**建议**: ⚠️ **可删除（PlatformSyncService 已禁用）**

---

### 9. ⚠️ **PlatformSyncService** - 平台同步服务（已禁用）

**作用**: 启动时自动与平台握手并拉取活动列表

**功能**:
- `platformBootstrapRunner()` - 启动时自动握手和拉取活动（已禁用）
- `getCachedActivities()` - 获取缓存的活动列表

**状态**:
- ❌ `@Bean` 已注释（功能已迁移到 DeviceBootstrapRunner）
- ❌ 仅被 PlatformConfigController 使用（已禁用）

**迁移**:
- 功能已迁移到 `DeviceBootstrapRunner`（启动时握手）

**建议**: ⚠️ **可删除（功能已迁移）**

---

### 10. ✅ **TemplateService** - 模板服务

**作用**: 提供模板列表（硬编码）

**功能**:
- `listTemplates()` - 返回硬编码的模板列表

**特点**:
- ✅ 简单直接，无平台依赖
- ✅ 被 TemplateController 和 SessionService 使用

**使用场景**:
- TemplateController 调用
- SessionService 调用（创建会话时选择模板）

**状态**: ✅ **使用中，保留**

---

## 🔄 功能重复分析

### 重复功能 1: 获取活动列表

| 类 | 路径 | 缓存 | 安全 | 状态 |
|---|------|------|------|------|
| **DevicePlatformController** | `/local/device/activities` | ✅ | ✅ | ✅ **使用** |
| **DeviceProxyController** | `/api/v1/device/activities` | ❌ | ❌ | ⚠️ **废弃** |
| **PlatformConfigController** | `/api/v1/platform/activities` | ✅ | ❌ | ⚠️ **废弃** |

**结论**: 以 **DevicePlatformController** 为准，其他两个可废弃。

---

### 重复功能 2: 平台 API 客户端

| 类 | 包 | 异常处理 | 使用情况 | 状态 |
|---|----|---------|---------|------|
| **PlatformDeviceApiClient** | `device` | ✅ PlatformCallException | ✅ 被使用 | ✅ **使用** |
| **PlatformDeviceApiClient** | `platform` | ❌ 简单异常 | ❌ 仅被禁用服务使用 | ⚠️ **废弃** |

**结论**: 以 **device 包的 PlatformDeviceApiClient** 为准，platform 包的可删除。

---

## 📊 使用情况统计

### 前端调用情况

| 接口 | 前端使用 | 状态 |
|------|---------|------|
| `GET /local/device/config` | ✅ Kiosk Settings | ✅ 使用中 |
| `PUT /local/device/config` | ✅ Kiosk Settings | ✅ 使用中 |
| `POST /local/device/handshake` | ✅ Kiosk D2 UI | ✅ 使用中 |
| `GET /local/device/activities` | ✅ Kiosk D2 UI | ✅ 使用中 |
| `GET /api/v1/device/activities` | ❌ 无 | ⚠️ 废弃 |
| `GET /api/v1/platform/activities` | ❌ 无 | ⚠️ 废弃 |
| `GET /api/v1/templates` | ✅ Kiosk | ✅ 使用中 |

---

## 🎯 推荐使用方案

### ✅ 核心类（必须保留）

1. **DeviceConfigController** - 设备配置管理
2. **DevicePlatformController** - D2 平台代理（handshake + activities）
3. **PlatformCallException** - 异常处理
4. **PlatformDeviceApiClient** (device包) - 平台 API 客户端
5. **TemplateController** - 模板列表
6. **TemplateService** - 模板服务

### ⚠️ 可废弃类（建议删除）

1. **DeviceProxyController** - 旧版代理（功能重复）
2. **PlatformConfigController** - 旧版平台配置（依赖已禁用服务）
3. **PlatformSyncService** - 已禁用（功能已迁移）
4. **PlatformDeviceApiClient** (platform包) - 旧版客户端（功能重复）

---

## 🔧 迁移建议

### 阶段 1: 标记废弃（当前）

```java
@Deprecated
@RestController
@RequestMapping("/api/v1/device")
public class DeviceProxyController {
    // ...
}
```

### 阶段 2: 检查依赖（1-2 周后）

- 确认无其他代码依赖废弃类
- 确认测试脚本已迁移

### 阶段 3: 删除废弃类（确认无依赖后）

删除以下文件：
- `DeviceProxyController.java`
- `PlatformConfigController.java`
- `PlatformSyncService.java`
- `platform/PlatformDeviceApiClient.java`

---

## 📝 总结

### 核心架构（保留）

```
DeviceConfigController (配置管理)
    ↓
DevicePlatformController (D2 平台代理)
    ↓
PlatformDeviceApiClient (device包) (API 客户端)
    ↓
PlatformCallException (异常处理)
```

### 废弃架构（可删除）

```
PlatformConfigController (旧版配置)
    ↓
PlatformSyncService (已禁用)
    ↓
PlatformDeviceApiClient (platform包) (旧版客户端)
```

**建议**: 保留核心类，删除废弃类，简化代码库。
