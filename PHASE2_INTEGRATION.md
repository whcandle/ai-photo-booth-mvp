# Phase 2: MVP ↔ CameraAgent 整合指南

## 整合目标

- MVP (Spring Boot) 通过 HTTP 调用 CameraAgent (C# OWIN Host)
- CameraAgent 负责所有相机操作（EDSDK + STA 线程 + 队列）
- MVP 只负责：生成路径、调用 API、处理业务逻辑

## 架构分工

```
┌─────────────────────────┐
│  ai-photo-booth-mvp     │
│  (Spring Boot)          │
│                         │
│  SessionService         │
│    ↓                    │
│  CameraService          │
│    ↓ (HTTP)             │
└──────────┬──────────────┘
           │
           │ POST /capture
           │ GET /status
           │
┌──────────▼──────────────┐
│  CameraAgent            │
│  (C# OWIN Host)         │
│                         │
│  CameraHostRuntime      │
│    ↓                    │
│  CameraCore.CameraService│
│    ↓                    │
│  STA Thread + Queue     │
│    ↓                    │
│  EDSDK                  │
└─────────────────────────┘
```

## 关键配置

### application.yml

```yaml
booth:
  sharedRawBaseDir: "D:/AICreama/booth/data/raw"  # MVP 生成路径用
  cameraAgentBaseUrl: "http://127.0.0.1:18080"    # CameraAgent 地址
  cameraAgentTimeoutMs: 65000                      # HTTP 超时（比 Agent 的 60s 稍大）
  cameraAgentCheckStatusBeforeCapture: true         # 拍照前检查状态（推荐）
```

### 路径约定

- **同一台机器**：使用绝对路径 `D:/AICreama/booth/data/raw/sess_{sessionId}/IMG_{timestamp}.jpg`
- **跨机器**：使用 UNC 路径 `\\\\HOST\\share\\...`，确保 CameraAgent 有写权限

## API 端点

### CameraAgent 端点

#### GET /status
```json
{
  "ok": true,
  "sdkInitialized": true,
  "sessionOpened": true,
  "cameraThreadId": 3,
  "apartmentState": "STA",
  "queueLength": 0,
  "cameraConnected": true,
  "error": null
}
```

#### POST /capture
```json
// 请求
{
  "targetFile": "D:/AICreama/booth/data/raw/sess_xxx/IMG_20250123_120000.jpg",
  "timeoutMs": 60000  // 可选
}

// 响应
{
  "ok": true,
  "path": "D:/AICreama/booth/data/raw/sess_xxx/IMG_20250123_120000.jpg",
  "size": 5773796,
  "elapsedMs": 623,
  "errorCode": 0,
  "error": null
}
```

### MVP 端点

#### GET /api/v1/health/camera
返回相机服务状态（新增）

```json
{
  "ok": true,
  "cameraStatus": {
    "ok": true,
    "cameraConnected": true,
    "cameraThreadId": 3,
    "apartmentState": "STA",
    "queueLength": 0,
    "sdkInitialized": true,
    "sessionOpened": true,
    "error": null
  },
  "timestamp": "2025-01-23T12:00:00Z"
}
```

## 已完成的修改

### 1. CameraService 接口扩展
- 添加 `getStatus()` 方法
- 添加 `CameraStatus` 内部类

### 2. CameraAgentCameraService 增强
- ✅ 增强错误信息（errorCode, elapsedMs, size）
- ✅ 实现 `getStatus()` 方法
- ✅ 添加 `assertCameraReady()` 方法（拍照前检查）
- ✅ 添加日志输出（elapsedMs, size, path）
- ✅ 支持配置项 `cameraAgentCheckStatusBeforeCapture`

### 3. HealthController 增强
- ✅ 添加 `/api/v1/health/camera` 端点
- ✅ 返回相机服务状态

### 4. application.yml 配置
- ✅ 调整超时时间（65000ms）
- ✅ 添加状态检查开关

## 测试步骤

### Step 1: 启动 CameraAgent

```bash
cd CameraAgent\bin\Debug
CameraAgent.exe
```

验证：
- 看到 "Server started. Listening on http://127.0.0.1:18080"
- 看到相机线程启动日志

### Step 2: 测试 CameraAgent 基本功能

```bash
# 检查状态
curl http://127.0.0.1:18080/status

# 拍照测试
curl -X POST http://127.0.0.1:18080/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"targetFile\":\"D:\\\\AICreama\\\\booth\\\\data\\\\raw\\\\test_001.jpg\"}"
```

### Step 3: 启动 MVP

```bash
# 在 Spring Boot 项目中
mvn spring-boot:run
```

### Step 4: 测试 MVP 相机健康检查

```bash
curl http://localhost:8080/api/v1/health/camera
```

### Step 5: 端到端测试（完整会话流程）

1. 创建会话：`POST /api/v1/sessions`
2. 选择模板：`POST /api/v1/sessions/{id}/select-template`
3. 拍照：`POST /api/v1/sessions/{id}/capture`
4. 验证文件生成：`D:/AICreama/booth/data/raw/sess_{sessionId}/IMG_{timestamp}.jpg`

### Step 6: 并发测试（验证排队）

**方法 A：直接压 CameraAgent**

```bash
# 开 3 个终端窗口，几乎同时执行：
curl -X POST http://127.0.0.1:18080/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"targetFile\":\"D:\\\\AICreama\\\\booth\\\\data\\\\raw\\\\conc_1.jpg\"}"

curl -X POST http://127.0.0.1:18080/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"targetFile\":\"D:\\\\AICreama\\\\booth\\\\data\\\\raw\\\\conc_2.jpg\"}"

curl -X POST http://127.0.0.1:18080/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"targetFile\":\"D:\\\\AICreama\\\\booth\\\\data\\\\raw\\\\conc_3.jpg\"}"
```

**方法 B：通过 MVP API 并发**

创建 3 个不同的 session，几乎同时调用 capture。

**验收标准：**
- ✅ 3 个请求都返回 `ok=true`
- ✅ 3 张照片都生成
- ✅ CameraAgent 日志显示串行执行（同一 threadId，时间上一个接一个）

## 常见问题

### 1. 路径问题

**问题**：文件未生成或路径错误

**解决**：
- 确认 `sharedRawBaseDir` 和 CameraAgent 的 `RawDir` 配置一致
- 确认路径使用正斜杠 `/` 或双反斜杠 `\\\\`
- 跨机器时使用 UNC 路径

### 2. 相机未连接

**问题**：`/status` 返回 `cameraConnected=false`

**解决**：
- 检查相机 USB 连接
- 关闭 EOS Utility 等占用相机的程序
- 查看 CameraAgent 日志中的错误信息

### 3. 超时问题

**问题**：请求超时

**解决**：
- 确认 `cameraAgentTimeoutMs` 配置合理（建议 65000ms）
- 检查相机响应是否正常
- 查看 CameraAgent 日志确认是否卡住

### 4. 并发请求处理

**问题**：并发请求是否排队？

**验证**：
- 查看 CameraAgent 日志，确认每次 capture 都在同一 threadId
- 查看 `/status` 的 `queueLength` 字段（并发瞬间会 > 0）
- 确认所有请求最终都成功返回

## 验收清单

- [ ] CameraAgent 启动成功，监听 `http://127.0.0.1:18080`
- [ ] `GET /status` 返回 `ok=true, cameraConnected=true`
- [ ] `POST /capture` 成功生成照片文件
- [ ] MVP 的 `/api/v1/health/camera` 返回相机状态
- [ ] 端到端流程：创建会话 → 选择模板 → 拍照 → 文件生成
- [ ] 并发 3 个请求全部成功（排队串行执行）
- [ ] 错误处理：相机未连接时返回明确错误信息

## 下一步

Phase 2 完成后，可以：
- Phase 3：增强错误处理和重试机制
- Phase 4：添加相机自动重连
- 后续：将 USB 摄像头也迁移到 CameraAgent
