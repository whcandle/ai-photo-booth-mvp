# Camera Config API 测试验收文档

## Phase C1: camera.json + Config API 验收

### 1. 准备工作

#### 1.1 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

#### 1.2 确认 MVP 端口
默认端口：`8080`（可在 `application.yml` 中查看 `server.port`）

---

## 2. 验收测试

### 2.1 测试 GET /local/camera/config（首次访问，自动生成默认配置）

**命令：**
```bash
curl http://localhost:8080/local/camera/config
```

**预期响应：**
```json
{
  "cameraModel": "Canon EOS R6",
  "selectedCameraId": "auto",
  "activePresetId": "preset_day_outdoor",
  "params": {
    "iso": 100,
    "whiteBalance": "DAYLIGHT",
    "exposureCompensationEv": 0.0,
    "pictureStyle": "STANDARD",
    "aperture": "F4.0",
    "shutterSpeed": "1/250",
    "meteringMode": "EVALUATIVE"
  },
  "presets": [
    {
      "id": "preset_day_outdoor",
      "name": "白天·室外",
      "tags": ["day", "outdoor"],
      "params": {
        "iso": 100,
        "whiteBalance": "DAYLIGHT",
        "exposureCompensationEv": 0.0,
        "pictureStyle": "STANDARD",
        "aperture": "F4.0",
        "shutterSpeed": "1/250",
        "meteringMode": "EVALUATIVE"
      }
    },
    {
      "id": "preset_night_indoor",
      "name": "夜晚·室内",
      "tags": ["night", "indoor"],
      "params": {
        "iso": 1600,
        "whiteBalance": "TUNGSTEN",
        "exposureCompensationEv": 0.3,
        "pictureStyle": "PORTRAIT",
        "aperture": "F2.8",
        "shutterSpeed": "1/60",
        "meteringMode": "CENTER_WEIGHTED"
      }
    },
    {
      "id": "preset_day_indoor",
      "name": "白天·室内",
      "tags": ["day", "indoor"],
      "params": {
        "iso": 400,
        "whiteBalance": "FLUORESCENT",
        "exposureCompensationEv": 0.0,
        "pictureStyle": "STANDARD",
        "aperture": "F4.0",
        "shutterSpeed": "1/125",
        "meteringMode": "EVALUATIVE"
      }
    },
    {
      "id": "preset_night_outdoor",
      "name": "夜晚·室外",
      "tags": ["night", "outdoor"],
      "params": {
        "iso": 800,
        "whiteBalance": "DAYLIGHT",
        "exposureCompensationEv": 0.0,
        "pictureStyle": "STANDARD",
        "aperture": "F2.8",
        "shutterSpeed": "1/125",
        "meteringMode": "EVALUATIVE"
      }
    }
  ],
  "ui": {
    "lockOnCountdown": true,
    "autoRestoreAfterSession": false
  }
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ 返回完整的 JSON 结构
- ✅ 包含 4 个默认 preset
- ✅ `params` 字段有值
- ✅ 首次访问后，运行目录下自动生成 `camera.json` 文件

**验证文件生成：**
```bash
# Windows PowerShell
Get-Content camera.json

# 或直接查看文件是否存在
Test-Path camera.json
```

---

### 2.2 测试 PUT /local/camera/config（修改配置并持久化）

**命令：**
```bash
curl -X PUT http://localhost:8080/local/camera/config ^
  -H "Content-Type: application/json" ^
  -d "{\"cameraModel\":\"Canon EOS R6\",\"selectedCameraId\":\"auto\",\"activePresetId\":\"preset_day_outdoor\",\"params\":{\"iso\":200,\"whiteBalance\":\"DAYLIGHT\",\"exposureCompensationEv\":0.0,\"pictureStyle\":\"STANDARD\",\"aperture\":\"F4.0\",\"shutterSpeed\":\"1/250\",\"meteringMode\":\"EVALUATIVE\"},\"presets\":[{\"id\":\"preset_day_outdoor\",\"name\":\"白天·室外\",\"tags\":[\"day\",\"outdoor\"],\"params\":{\"iso\":200,\"whiteBalance\":\"DAYLIGHT\",\"exposureCompensationEv\":0.0,\"pictureStyle\":\"STANDARD\",\"aperture\":\"F4.0\",\"shutterSpeed\":\"1/250\",\"meteringMode\":\"EVALUATIVE\"}}],\"ui\":{\"lockOnCountdown\":true,\"autoRestoreAfterSession\":false}}"
```

**或者使用文件方式（推荐）：**

1. 创建 `test-config.json`：
```json
{
  "cameraModel": "Canon EOS R6",
  "selectedCameraId": "auto",
  "activePresetId": "preset_day_outdoor",
  "params": {
    "iso": 200,
    "whiteBalance": "DAYLIGHT",
    "exposureCompensationEv": 0.0,
    "pictureStyle": "STANDARD",
    "aperture": "F4.0",
    "shutterSpeed": "1/250",
    "meteringMode": "EVALUATIVE"
  },
  "presets": [
    {
      "id": "preset_day_outdoor",
      "name": "白天·室外",
      "tags": ["day", "outdoor"],
      "params": {
        "iso": 200,
        "whiteBalance": "DAYLIGHT",
        "exposureCompensationEv": 0.0,
        "pictureStyle": "STANDARD",
        "aperture": "F4.0",
        "shutterSpeed": "1/250",
        "meteringMode": "EVALUATIVE"
      }
    }
  ],
  "ui": {
    "lockOnCountdown": true,
    "autoRestoreAfterSession": false
  }
}
```

2. 使用文件发送请求：
```bash
curl -X PUT http://localhost:8080/local/camera/config ^
  -H "Content-Type: application/json" ^
  -d @test-config.json
```

**预期响应：**
```json
{
  "success": true,
  "message": "Config saved successfully"
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ 返回 `success: true`
- ✅ `camera.json` 文件内容已更新（ISO 从 100 改为 200）

**验证持久化：**
```bash
# 1. 查看文件内容
Get-Content camera.json | ConvertFrom-Json | Select-Object -ExpandProperty params | Select-Object iso

# 应该显示：iso = 200

# 2. 重启 MVP，再次 GET
curl http://localhost:8080/local/camera/config

# 应该返回 ISO = 200（证明持久化成功）
```

---

### 2.3 测试 localhost 访问限制

**命令（从非 localhost 访问，应该被拒绝）：**
```bash
# 使用外部 IP 访问（如果 MVP 绑定到 0.0.0.0）
curl http://<外部IP>:8080/local/camera/config
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

### 2.4 测试部分更新（PUT 支持全量覆盖）

**说明：** PUT 接口支持全量覆盖，即可以只传部分字段，但建议传完整结构。

**测试步骤：**
1. 先 GET 获取完整配置
2. 修改 `params.iso` 为 400
3. PUT 保存
4. 再次 GET 验证

**命令：**
```bash
# 1. 获取当前配置
curl http://localhost:8080/local/camera/config > current-config.json

# 2. 手动编辑 current-config.json，修改 params.iso = 400

# 3. PUT 保存
curl -X PUT http://localhost:8080/local/camera/config ^
  -H "Content-Type: application/json" ^
  -d @current-config.json

# 4. 验证
curl http://localhost:8080/local/camera/config | findstr "iso"
```

**验收点：**
- ✅ PUT 后，GET 返回的 `params.iso` 为 400
- ✅ 文件 `camera.json` 中 `params.iso` 为 400

---

## 3. 验收清单

### ✅ 功能验收
- [x] GET /local/camera/config 返回完整 JSON
- [x] 首次访问自动生成 `camera.json`（包含 4 个默认 preset）
- [x] PUT /local/camera/config 成功保存配置
- [x] 配置持久化到文件（重启后仍有效）
- [x] localhost 访问限制生效（非 localhost 返回 403）

### ✅ 文件验收
- [x] `camera.json` 文件在运行目录生成
- [x] JSON 格式正确，可读性强（pretty print）
- [x] 文件内容与 API 返回一致

### ✅ 日志验收
启动 MVP 后，查看控制台日志：
- [x] 首次访问时看到：`[camera-config] camera.json not found at ..., creating default config`
- [x] 保存时看到：`[camera-config] camera.json saved to ...`

---

## 4. 常见问题

### Q1: 返回 403 Forbidden
**原因：** 请求不是从 localhost 发出  
**解决：** 使用 `http://localhost:8080` 或 `http://127.0.0.1:8080`

### Q2: PUT 后文件没有更新
**原因：** 可能是 JSON 格式错误  
**解决：** 检查请求体 JSON 格式，确保是有效的 JSON

### Q3: 文件路径不对
**原因：** `camera.json` 应该在 MVP 运行目录（通常是项目根目录）  
**解决：** 检查 `application.yml` 中的 `booth.cameraConfigFile` 配置

---

## 5. 下一步（Phase C2）

完成 Phase C1 验收后，可以继续：
- Phase C2: 实现 `/local/camera/status` 和 `/local/camera/test-shot`
- Phase C3: 实现 `/local/camera/presets/apply` 和 `/local/camera/apply-params`
