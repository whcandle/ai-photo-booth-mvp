# Kiosk 设备 API 设计工作总结

## 概述

本次会话主要完成了 **Kiosk 设备与平台交互的 API 设计**，实现了设备身份管理、平台同步、本地代理等核心功能，确保 MVP 能够优雅地处理配置缺失和网络异常情况。

---

## 一、核心设计目标

1. **非阻塞启动**：确保 MVP 在配置缺失或平台同步失败时仍能正常启动
2. **设备身份管理**：通过 `device.json` 管理设备身份信息（deviceCode, secret, deviceId, token）
3. **平台同步机制**：自动完成设备握手（handshake）和活动列表（activities）拉取
4. **本地代理接口**：为 Kiosk 提供本地 API，避免直接连接平台
5. **统一响应格式**：所有 API 返回统一的 JSON 结构（`success`, `data`, `message`）

---

## 二、实现的功能模块

### 2.1 设备身份管理（Device Identity）

#### 文件结构
- **`device.json`**：设备身份配置文件
  ```json
  {
    "deviceCode": "dev_001",
    "secret": "your-secret",
    "deviceId": 123,
    "token": "device-token",
    "tokenExpiresAt": "2024-01-01T00:00:00Z"
  }
  ```

#### 核心类
- **`DeviceIdentity.java`**：设备身份数据模型（POJO）
- **`DeviceIdentityStore.java`**：设备身份存储工具类
  - `load()`：加载 `device.json`，返回 `Optional<DeviceIdentity>`
  - `save()`：保存设备身份到 `device.json`
  - `isTokenValid()`：检查 token 是否有效
  - **非阻塞设计**：文件不存在或配置不完整时返回 `Optional.empty()`，记录 WARN 日志

#### 关键特性
- ✅ 文件不存在时不会抛出异常，返回 `Optional.empty()`
- ✅ 配置字段缺失时记录警告日志，不阻塞启动
- ✅ 自动检查 token 过期时间

---

### 2.2 平台设备 API 客户端（PlatformDeviceApiClient）

#### 核心类
- **`PlatformDeviceApiClient.java`**：平台设备 API 客户端
  - 使用 Spring `RestTemplate` 进行 HTTP 调用
  - 处理 HTTP 错误和业务错误
  - 自动规范化 base URL（去除末尾斜杠）

#### 主要方法

##### 1. `handshake(String deviceCode, String secret)`
- **功能**：设备握手，获取 `deviceId`、`deviceToken`、`tokenExpiresAt`
- **端点**：`POST /api/v1/device/handshake`
- **请求体**：
  ```json
  {
    "deviceCode": "dev_001",
    "secret": "your-secret"
  }
  ```
- **响应处理**：
  - 检查 HTTP 状态码（2xx）
  - 检查业务响应中的 `success` 字段
  - 提取 `data.deviceId`、`data.deviceToken`、`data.tokenExpiresAt`
- **错误处理**：
  - HTTP 错误：抛出 `RuntimeException`，包含状态码和错误信息
  - 业务错误：检查 `success: false`，抛出异常

##### 2. `listActivities(Long deviceId, String token)`
- **功能**：拉取设备活动列表
- **端点**：`GET /api/v1/device/{deviceId}/activities`
- **请求头**：`Authorization: Bearer {token}`
- **响应**：返回活动列表数组
- **错误处理**：同 `handshake()`

#### 关键特性
- ✅ 自动规范化 base URL（去除末尾斜杠）
- ✅ 统一的错误处理机制
- ✅ 支持业务错误和 HTTP 错误分离
- ✅ 使用 `@Component("devicePlatformDeviceApiClient")` 避免 Bean 冲突

---

### 2.3 设备启动引导（DeviceBootstrapRunner）

#### 核心类
- **`DeviceBootstrapRunner.java`**：实现 `ApplicationRunner`，在应用启动后执行

#### 启动流程
```
1. 读取 device.json
   ├─ 文件不存在 → 记录 WARN，跳过后续步骤
   ├─ deviceCode/secret 缺失 → 记录 WARN，跳过后续步骤
   └─ 配置完整 → 继续

2. 检查 token 有效性
   ├─ token 无效或过期 → 执行 handshake
   └─ token 有效 → 跳过 handshake

3. 执行 handshake（如需要）
   ├─ 成功 → 保存 deviceId、token、tokenExpiresAt 到 device.json
   └─ 失败 → 记录 ERROR，继续执行（不阻塞启动）

4. 拉取 activities
   ├─ 成功 → 记录 INFO，打印活动列表
   └─ 失败 → 记录 ERROR，继续执行（不阻塞启动）
```

#### 关键特性
- ✅ **完全非阻塞**：所有异常都被捕获，不会导致应用启动失败
- ✅ **优雅降级**：配置缺失或网络异常时，应用仍能正常启动
- ✅ 使用 `@Qualifier` 注入特定的 `DeviceIdentityStore` 和 `PlatformDeviceApiClient` Bean
- ✅ `@Bean` 方法名为 `deviceBootstrapApplicationRunner` 避免命名冲突

---

### 2.4 本地代理接口（DeviceProxyController）

#### 核心类
- **`DeviceProxyController.java`**：REST 控制器，为 Kiosk 提供本地代理 API

#### 主要端点

##### `GET /api/v1/device/activities`
- **功能**：获取设备活动列表（代理到平台）
- **请求**：无需参数
- **响应格式**：
  ```json
  {
    "success": true,
    "data": [
      {
        "id": 1,
        "name": "Activity 1",
        "status": "active"
      }
    ],
    "message": null
  }
  ```
- **错误处理**：
  - `device.json` 不存在 → `success: false, message: "device.json not found"`
  - `deviceId` 或 `token` 缺失 → `success: false, message: "Device not handshaked"`
  - API 调用失败 → `success: false, message: "Failed to fetch activities: {error}"`

#### 实现逻辑
1. 加载 `device.json`（使用 `DeviceIdentityStore`）
2. 检查 `deviceId` 和 `token` 是否存在
3. 调用 `PlatformDeviceApiClient.listActivities(deviceId, token)`
4. 返回统一格式的 JSON 响应

#### 关键特性
- ✅ **本地代理**：Kiosk 无需直接连接平台，只需调用本地 MVP API
- ✅ **统一响应格式**：所有响应遵循 `{success, data, message}` 结构
- ✅ **错误处理**：清晰的错误消息，便于前端处理
- ✅ **无认证要求**：本地接口，不引入额外认证机制

---

## 三、技术实现细节

### 3.1 Bean 冲突解决

由于存在多个同名类（如 `DeviceIdentityStore` 在不同包中），采用以下策略：

1. **显式 Bean 命名**：使用 `@Component("customBeanName")` 指定唯一名称
2. **依赖注入限定**：使用 `@Qualifier("customBeanName")` 明确指定注入的 Bean

**示例**：
```java
@Component("deviceDeviceIdentityStore")
public class DeviceIdentityStore { ... }

@Component("devicePlatformDeviceApiClient")
public class PlatformDeviceApiClient { ... }

@Bean
public ApplicationRunner deviceBootstrapApplicationRunner(
    @Qualifier("deviceDeviceIdentityStore") DeviceIdentityStore identityStore,
    @Qualifier("devicePlatformDeviceApiClient") PlatformDeviceApiClient apiClient
) { ... }
```

### 3.2 非阻塞设计模式

所有可能失败的操作都采用以下模式：

```java
try {
    // 执行操作
    Optional<DeviceIdentity> identity = identityStore.load();
    if (identity.isEmpty()) {
        log.warn("device.json not found, skipping bootstrap");
        return;
    }
    
    // 继续执行...
} catch (Exception e) {
    log.error("Bootstrap failed, but application will continue", e);
    // 不抛出异常，确保应用继续启动
}
```

### 3.3 统一响应格式

所有 API 响应遵循以下结构：

```json
{
  "success": boolean,
  "data": any,
  "message": string | null
}
```

**成功响应**：
```json
{
  "success": true,
  "data": { ... },
  "message": null
}
```

**失败响应**：
```json
{
  "success": false,
  "data": null,
  "message": "Error description"
}
```

---

## 四、文件清单

### 4.1 核心实现文件

| 文件路径 | 说明 |
|---------|------|
| `src/main/java/com/mg/booth/device/DeviceIdentity.java` | 设备身份数据模型 |
| `src/main/java/com/mg/booth/device/DeviceIdentityStore.java` | 设备身份存储工具 |
| `src/main/java/com/mg/booth/device/PlatformDeviceApiClient.java` | 平台设备 API 客户端 |
| `src/main/java/com/mg/booth/device/DeviceBootstrapRunner.java` | 设备启动引导器 |
| `src/main/java/com/mg/booth/device/HandshakeData.java` | 握手响应数据模型 |
| `src/main/java/com/mg/booth/api/DeviceProxyController.java` | 本地代理控制器（已删除） |

### 4.2 配置文件

| 文件路径 | 说明 |
|---------|------|
| `device.json` | 设备身份配置文件（运行时生成） |
| `device.json.example` | 设备身份配置示例文件 |

### 4.3 文档文件

| 文件路径 | 说明 |
|---------|------|
| `DEVICE_API_CLIENT_README.md` | 平台设备 API 客户端文档 |
| `DEVICE_BOOTSTRAP_README.md` | 设备启动引导文档 |
| `STEP_B1_README.md` | 非阻塞启动行为说明 |

---

## 五、API 端点总结

### 5.1 平台 API（PlatformDeviceApiClient 调用）

| 方法 | 端点 | 说明 |
|------|------|------|
| `POST` | `/api/v1/device/handshake` | 设备握手 |
| `GET` | `/api/v1/device/{deviceId}/activities` | 获取设备活动列表 |

### 5.2 本地代理 API（DeviceProxyController，已删除）

| 方法 | 端点 | 说明 |
|------|------|------|
| `GET` | `/api/v1/device/activities` | 获取设备活动列表（代理） |

---

## 六、关键设计决策

### 6.1 为什么使用 Optional？

- **非阻塞启动**：`Optional.empty()` 表示配置缺失，但不抛出异常
- **明确语义**：调用方必须显式处理缺失情况
- **避免 NPE**：减少空指针异常风险

### 6.2 为什么分离 DeviceIdentityStore？

- **职责分离**：设备身份管理与平台同步逻辑分离
- **可测试性**：便于单元测试和模拟
- **复用性**：多个组件可以复用身份存储功能

### 6.3 为什么使用 ApplicationRunner？

- **启动时机**：在 Spring 上下文完全初始化后执行
- **非阻塞**：即使失败也不会阻止应用启动
- **自动执行**：无需手动触发

### 6.4 为什么需要本地代理？

- **解耦**：Kiosk 不需要知道平台 API 的具体实现
- **简化**：Kiosk 只需调用本地 MVP API
- **灵活性**：未来可以在代理层添加缓存、重试等逻辑

---

## 七、测试与验证

### 7.1 启动测试

1. **无 device.json**：应用正常启动，记录 WARN 日志
2. **device.json 配置不完整**：应用正常启动，记录 WARN 日志
3. **平台不可用**：应用正常启动，handshake 和 activities 拉取失败，记录 ERROR 日志
4. **正常流程**：应用启动 → 读取配置 → handshake → 拉取 activities → 记录 INFO 日志

### 7.2 API 测试

使用 PowerShell 或 curl 测试本地代理 API：

```powershell
# 获取活动列表
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/device/activities" -Method GET
```

---

## 八、后续扩展方向

1. **Token 自动刷新**：在 `DeviceBootstrapRunner` 中检测 token 即将过期时自动刷新
2. **Activities 缓存**：在 `DeviceProxyController` 中缓存活动列表，减少平台调用
3. **重试机制**：为 `PlatformDeviceApiClient` 添加重试逻辑
4. **健康检查**：添加设备健康检查端点，报告设备状态和平台连接状态
5. **配置热更新**：支持在不重启应用的情况下更新 `device.json`

---

## 九、总结

本次会话完成了 **Kiosk 设备 API 设计的核心功能**：

✅ **设备身份管理**：通过 `device.json` 管理设备身份，支持非阻塞加载  
✅ **平台同步机制**：自动完成 handshake 和 activities 拉取，失败不阻塞启动  
✅ **本地代理接口**：为 Kiosk 提供统一的本地 API，解耦平台依赖  
✅ **统一响应格式**：所有 API 返回一致的 JSON 结构  
✅ **优雅降级**：配置缺失或网络异常时，应用仍能正常启动  

这些功能确保了 MVP 的**高可用性**和**可维护性**，为后续的 Kiosk 集成和相机控制功能奠定了基础。
