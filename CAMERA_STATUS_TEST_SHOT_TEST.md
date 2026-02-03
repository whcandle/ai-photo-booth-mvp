# Camera Status + Test Shot API 测试验收文档

## Phase C2: CameraStatus + TestShot 验收

### 1. 准备工作

#### 1.1 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

#### 1.2 确认相机服务
- 如果使用 `CameraAgentCameraService`，确保 CameraAgent 服务已启动（默认端口 18080）
- 如果使用 `MockCameraService`，测试拍照会生成模拟文件

---

## 2. 验收测试

### 2.1 测试 GET /local/camera/status（相机状态查询）

**命令：**
```bash
curl http://localhost:8080/local/camera/status
```

**预期响应（相机已连接）：**
```json
{
  "success": true,
  "data": {
    "connected": true,
    "cameraModel": "Canon EOS R6",
    "battery": null,
    "lastError": null,
    "ok": true,
    "sdkInitialized": true,
    "sessionOpened": true
  },
  "message": null
}
```

**预期响应（相机未连接）：**
```json
{
  "success": false,
  "data": {
    "connected": false,
    "cameraModel": "Canon EOS R6",
    "battery": null,
    "lastError": "Camera not connected",
    "ok": false,
    "sdkInitialized": null,
    "sessionOpened": null
  },
  "message": "Failed to get camera status: ..."
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ `success` 字段明确表示状态查询是否成功
- ✅ `data.connected` 字段明确表示相机是否连接（true/false）
- ✅ `data.cameraModel` 从 `camera.json` 读取
- ✅ 相机未连接时，`lastError` 包含明确的错误信息

---

### 2.2 测试 POST /local/camera/test-shot（测试拍照）

**命令：**
```bash
curl -X POST http://localhost:8080/local/camera/test-shot
```

**预期响应（成功）：**
```json
{
  "success": true,
  "data": {
    "path": "D:\\workspace\\ai-photo-booth-mvp\\test\\test_20260201_120102.jpg"
  },
  "message": null
}
```

**预期响应（相机未连接）：**
```json
{
  "success": false,
  "data": null,
  "message": "Camera not ready: Camera not connected"
}
```

**预期响应（拍照失败）：**
```json
{
  "success": false,
  "data": null,
  "message": "Failed to capture test shot: ..."
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ 成功时，`data.path` 包含保存的文件路径（绝对路径）
- ✅ 文件保存在 `./test/` 目录下
- ✅ 文件名格式：`test_yyyyMMdd_HHmmss.jpg`
- ✅ 相机未连接时，返回明确的错误信息（`Camera not ready`）
- ✅ 拍照失败时，返回详细的错误信息

**验证文件生成：**
```bash
# PowerShell - 检查 test 目录
Test-Path test

# 列出 test 目录下的文件
Get-ChildItem test -Filter "test_*.jpg"

# 查看最新文件
Get-ChildItem test -Filter "test_*.jpg" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
```

---

### 2.3 测试完整流程（状态查询 → 测试拍照）

**步骤 1：查询状态**
```bash
curl http://localhost:8080/local/camera/status
```

**步骤 2：如果 connected=true，执行测试拍照**
```bash
curl -X POST http://localhost:8080/local/camera/test-shot
```

**步骤 3：验证文件**
```bash
# 检查文件是否存在
Test-Path test\test_*.jpg

# 查看文件大小（应该 > 0）
Get-ChildItem test -Filter "test_*.jpg" | Select-Object Name, Length, LastWriteTime
```

---

### 2.4 测试 localhost 访问限制

**命令（从非 localhost 访问，应该被拒绝）：**
```bash
# 使用外部 IP 访问（如果 MVP 绑定到 0.0.0.0）
curl http://<外部IP>:8080/local/camera/status
```

**预期响应：**
```json
{
  "success": false,
  "message": "Access denied: only localhost allowed"
}
```

**HTTP 状态码：** `403 Forbidden`

**验收点：**
- ✅ 非 localhost 访问返回 403
- ✅ 错误消息明确说明只允许 localhost

---

## 3. 验收清单

### ✅ 功能验收
- [x] GET /local/camera/status 返回相机状态
- [x] `data.connected` 字段明确表示连接状态（true/false）
- [x] 相机未连接时返回明确的错误信息
- [x] POST /local/camera/test-shot 成功拍照并保存文件
- [x] 测试照片保存在 `./test/` 目录
- [x] 文件名包含时间戳（`test_yyyyMMdd_HHmmss.jpg`）
- [x] 相机未连接时返回明确的错误信息
- [x] localhost 访问限制生效（非 localhost 返回 403）

### ✅ 文件验收
- [x] `test/` 目录自动创建（如果不存在）
- [x] 测试照片文件成功生成
- [x] 文件路径为绝对路径

### ✅ 日志验收
启动 MVP 后，查看控制台日志：
- [x] 测试拍照成功时看到：`[camera-test-shot] Test shot saved: ...`
- [x] 测试拍照失败时看到：`[camera-test-shot] Failed to capture test shot: ...`

---

## 4. 常见问题

### Q1: 返回 `connected: false` 但相机实际已连接
**原因：** CameraAgent 服务未启动或连接失败  
**解决：** 
- 检查 CameraAgent 服务是否运行（默认端口 18080）
- 检查 `application.yml` 中的 `booth.cameraAgentBaseUrl` 配置
- 查看 MVP 日志中的错误信息

### Q2: test-shot 返回成功但文件不存在
**原因：** 可能是权限问题或路径问题  
**解决：** 
- 检查 MVP 运行目录的写入权限
- 检查 `test/` 目录是否成功创建
- 查看 MVP 日志中的详细错误信息

### Q3: 使用 MockCameraService 时 test-shot 总是成功
**原因：** `MockCameraService` 是模拟实现，不实际拍照  
**解决：** 这是正常的，Mock 服务用于开发测试，不会生成真实的照片文件

### Q4: 文件路径中的反斜杠问题
**原因：** Windows 路径使用反斜杠，JSON 中需要转义  
**解决：** 这是正常的，JSON 中的路径字符串会自动转义

---

## 5. 下一步（Phase C3）

完成 Phase C2 验收后，可以继续：
- Phase C3: 实现 `/local/camera/presets/apply` 和 `/local/camera/apply-params`
- Phase C4: Kiosk Settings 页面 + CameraTab（只读展示）
