# 完整 API 调用示例文档

## 📋 目录

1. [MVP 本地相机 API](#mvp-本地相机-api)
2. [CameraAgent API](#cameraagent-api)
3. [完整工作流示例](#完整工作流示例)
4. [错误处理示例](#错误处理示例)

---

## MVP 本地相机 API

**基础 URL：** `http://localhost:8080`  
**限制：** 所有接口只允许 localhost 访问

---

### 1. 获取相机配置

**接口：** `GET /local/camera/config`

**功能：** 获取完整的相机配置（包括所有 presets 和当前参数）

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Get | ConvertTo-Json -Depth 10
```

**curl.exe 命令：**
```bash
curl.exe http://localhost:8080/local/camera/config
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
      "displayName": "白天·室外",
      "category": "ENV",
      "tags": ["day", "outdoor"],
      "legacyProfileId": null,
      "params": { ... }
    },
    ...
  ],
  "ui": {
    "lockOnCountdown": true,
    "autoRestoreAfterSession": false
  }
}
```

---

### 2. 保存相机配置

**接口：** `PUT /local/camera/config`

**功能：** 保存完整的相机配置（全量覆盖）

**PowerShell 命令：**
```powershell
# 1. 先获取当前配置
$config = Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Get

# 2. 修改配置（例如修改 ISO）
$config.params.iso = 200

# 3. 保存配置
$body = $config | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Put -Body $body -ContentType "application/json"
```

**curl.exe 命令：**
```bash
# 1. 获取配置
curl.exe http://localhost:8080/local/camera/config -o config.json

# 2. 编辑 config.json（手动修改）

# 3. 保存配置
curl.exe -X PUT http://localhost:8080/local/camera/config -H "Content-Type: application/json" -d @config.json
```

**预期响应：**
```json
{
  "success": true,
  "message": "Config saved successfully"
}
```

---

### 3. 获取相机状态

**接口：** `GET /local/camera/status`

**功能：** 获取相机连接状态和详细信息

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/status" -Method Get | ConvertTo-Json -Depth 5
```

**curl.exe 命令：**
```bash
curl.exe http://localhost:8080/local/camera/status
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "ok": true,
    "cameraConnected": true,
    "error": null,
    "cameraThreadId": 3,
    "apartmentState": "STA",
    "queueLength": 0,
    "sdkInitialized": true,
    "sessionOpened": true
  },
  "message": null
}
```

---

### 4. 测试拍照

**接口：** `POST /local/camera/test-shot`

**功能：** 触发测试拍照，保存到 `./test/` 目录

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/test-shot" -Method Post | ConvertTo-Json
```

**curl.exe 命令：**
```bash
curl.exe -X POST http://localhost:8080/local/camera/test-shot
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "path": "./test/20260131_123456.jpg"
  },
  "message": null
}
```

**错误响应（相机未连接）：**
```json
{
  "success": false,
  "data": null,
  "message": "Camera not connected"
}
```

---

### 5. 应用相机参数（部分更新）

**接口：** `POST /local/camera/apply-params`

**功能：** 应用相机参数，支持部分更新（只更新提供的字段）

**PowerShell 命令：**
```powershell
# 示例 1: 只更新 ISO 和白平衡
$body = @{
    iso = 400
    whiteBalance = "FLUORESCENT"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"

# 示例 2: 更新多个参数
$body = @{
    iso = 1600
    whiteBalance = "TUNGSTEN"
    exposureCompensationEv = 0.3
    pictureStyle = "PORTRAIT"
    aperture = "F2.8"
    shutterSpeed = "1/60"
    meteringMode = "CENTER_WEIGHTED"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"
```

**curl.exe 命令：**
```bash
# 示例 1: 只更新 ISO
curl.exe -X POST http://localhost:8080/local/camera/apply-params -H "Content-Type: application/json" -d "{\"iso\":400}"

# 示例 2: 更新多个参数
curl.exe -X POST http://localhost:8080/local/camera/apply-params -H "Content-Type: application/json" -d "{\"iso\":1600,\"whiteBalance\":\"TUNGSTEN\",\"exposureCompensationEv\":0.3,\"pictureStyle\":\"PORTRAIT\",\"aperture\":\"F2.8\",\"shutterSpeed\":\"1/60\",\"meteringMode\":\"CENTER_WEIGHTED\"}"
```

**预期响应（成功）：**
```json
{
  "success": true,
  "data": {
    "applied": true
  },
  "message": null
}
```

**预期响应（部分失败）：**
```json
{
  "success": false,
  "data": {
    "applied": false,
    "failedFields": {
      "ISO": "CameraAgent setProperty failed: key=ISO, value=99999, error=..."
    },
    "failedField": "ISO",
    "reason": "CameraAgent setProperty failed: key=ISO, value=99999, error=..."
  },
  "message": "Some parameters failed to apply"
}
```

---

### 6. 应用预设套餐

**接口：** `POST /local/camera/presets/apply`

**功能：** 应用预设套餐（环境预设或业务场景预设）

**PowerShell 命令：**
```powershell
# 示例 1: 应用环境预设
$body = @{
    presetId = "preset_night_indoor"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"

# 示例 2: 应用业务场景预设
$body = @{
    presetId = "preset_business_idphoto"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**curl.exe 命令：**
```bash
# 应用环境预设
curl.exe -X POST http://localhost:8080/local/camera/presets/apply -H "Content-Type: application/json" -d "{\"presetId\":\"preset_night_indoor\"}"

# 应用业务场景预设
curl.exe -X POST http://localhost:8080/local/camera/presets/apply -H "Content-Type: application/json" -d "{\"presetId\":\"preset_business_idphoto\"}"
```

**预期响应（成功）：**
```json
{
  "success": true,
  "data": {
    "applied": true
  },
  "message": null
}
```

**预期响应（预设不存在）：**
```json
{
  "success": false,
  "data": null,
  "message": "Preset not found: preset_xxx"
}
```

---

### 7. 获取预设列表

**接口：** `GET /local/camera/presets`

**功能：** 获取所有可用的预设列表（包括环境预设和业务场景预设）

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets" -Method Get | ConvertTo-Json -Depth 10
```

**curl.exe 命令：**
```bash
curl.exe http://localhost:8080/local/camera/presets
```

**预期响应：**
```json
{
  "success": true,
  "data": [
    {
      "id": "preset_day_outdoor",
      "name": "白天·室外",
      "displayName": "白天·室外",
      "category": "ENV",
      "tags": ["day", "outdoor"],
      "legacyProfileId": null,
      "paramsPreview": {
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
      "id": "preset_business_medical",
      "name": "医疗/养老标准记录",
      "displayName": "医疗/养老标准记录",
      "category": "BUSINESS",
      "tags": ["business", "medical", "elderly"],
      "legacyProfileId": "medical_standard",
      "paramsPreview": { ... }
    },
    ...
  ],
  "message": null
}
```

---

### 8. 更新预设参数

**接口：** `PUT /local/camera/presets/{presetId}/params`

**功能：** 更新指定预设的参数定义（永久修改）

**PowerShell 命令：**
```powershell
# 示例 1: 完整更新所有参数
$body = @{
    iso = 200
    whiteBalance = "DAYLIGHT"
    exposureCompensationEv = 0.3
    pictureStyle = "PORTRAIT"
    aperture = "F5.6"
    shutterSpeed = "1/500"
    meteringMode = "CENTER_WEIGHTED"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_day_outdoor/params" -Method Put -Body $body -ContentType "application/json"

# 示例 2: 部分更新（只更新 ISO 和白平衡）
$body = @{
    iso = 400
    whiteBalance = "FLUORESCENT"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_day_outdoor/params" -Method Put -Body $body -ContentType "application/json"
```

**curl.exe 命令：**
```bash
# 完整更新
curl.exe -X PUT http://localhost:8080/local/camera/presets/preset_day_outdoor/params -H "Content-Type: application/json" -d "{\"iso\":200,\"whiteBalance\":\"DAYLIGHT\",\"exposureCompensationEv\":0.3,\"pictureStyle\":\"PORTRAIT\",\"aperture\":\"F5.6\",\"shutterSpeed\":\"1/500\",\"meteringMode\":\"CENTER_WEIGHTED\"}"

# 部分更新
curl.exe -X PUT http://localhost:8080/local/camera/presets/preset_day_outdoor/params -H "Content-Type: application/json" -d "{\"iso\":400,\"whiteBalance\":\"FLUORESCENT\"}"
```

**预期响应（成功）：**
```json
{
  "success": true,
  "data": {
    "presetId": "preset_day_outdoor",
    "updatedParams": {
      "iso": 200,
      "whiteBalance": "DAYLIGHT",
      "exposureCompensationEv": 0.3,
      "pictureStyle": "PORTRAIT",
      "aperture": "F5.6",
      "shutterSpeed": "1/500",
      "meteringMode": "CENTER_WEIGHTED"
    }
  },
  "message": null
}
```

**预期响应（预设不存在）：**
```json
{
  "success": false,
  "data": null,
  "message": "Preset not found: preset_xxx"
}
```

**预期响应（Legacy Preset 不允许修改）：**
```json
{
  "success": false,
  "data": null,
  "message": "Cannot update params for legacy preset (preset uses legacyProfileId). PresetId: preset_business_idphoto"
}
```

---

## CameraAgent API

**基础 URL：** `http://localhost:18080`  
**说明：** CameraAgent 是 C# 服务，提供底层相机控制接口

---

### 1. 获取属性值

**接口：** `GET /property/get?key={key}`

**功能：** 获取相机属性的当前值（EDSDK 编码值）

**支持的 key：**
- `ISO`
- `WB`
- `ExposureComp`
- `PictureStyle`
- `APERTURE`
- `SHUTTER_SPEED`
- `METERING_MODE`

**PowerShell 命令：**
```powershell
# 获取 ISO
Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=ISO" -Method Get

# 获取光圈
Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=APERTURE" -Method Get

# 批量获取所有参数
$params = @("ISO", "WB", "PictureStyle", "ExposureComp", "APERTURE", "SHUTTER_SPEED", "METERING_MODE")
foreach ($param in $params) {
    $result = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=$param" -Method Get
    Write-Host "$param = $($result.value) (ok=$($result.ok))"
}
```

**curl.exe 命令：**
```bash
curl.exe http://localhost:18080/property/get?key=ISO
curl.exe http://localhost:18080/property/get?key=APERTURE
curl.exe http://localhost:18080/property/get?key=SHUTTER_SPEED
```

**预期响应（成功）：**
```json
{
  "ok": true,
  "key": "ISO",
  "value": 104
}
```

**预期响应（失败）：**
```json
{
  "ok": false,
  "key": "ISO",
  "error": "Camera not connected"
}
```

---

### 2. 设置属性值

**接口：** `POST /property/set`

**功能：** 设置相机属性值（使用 EDSDK 编码值）

**PowerShell 命令：**
```powershell
# 设置 ISO (104 = ISO 1600)
$body = @{
    Key = "ISO"
    Value = 104
    Persist = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:18080/property/set" -Method Post -Body $body -ContentType "application/json"

# 设置光圈 (24 = F2.8)
$body = @{
    Key = "APERTURE"
    Value = 24
    Persist = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:18080/property/set" -Method Post -Body $body -ContentType "application/json"

# 设置快门速度 (48 = 1/60s)
$body = @{
    Key = "SHUTTER_SPEED"
    Value = 48
    Persist = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:18080/property/set" -Method Post -Body $body -ContentType "application/json"

# 设置测光模式 (5 = CENTER_WEIGHTED)
$body = @{
    Key = "METERING_MODE"
    Value = 5
    Persist = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:18080/property/set" -Method Post -Body $body -ContentType "application/json"
```

**curl.exe 命令：**
```bash
# 设置 ISO
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"ISO\",\"Value\":104,\"Persist\":false}"

# 设置光圈
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"APERTURE\",\"Value\":24,\"Persist\":false}"

# 设置快门速度
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"SHUTTER_SPEED\",\"Value\":48,\"Persist\":false}"

# 设置测光模式
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"METERING_MODE\",\"Value\":5,\"Persist\":false}"
```

**预期响应（成功）：**
```json
{
  "ok": true
}
```

**预期响应（失败）：**
```json
{
  "ok": false,
  "error": "CameraAgent setProperty failed: key=ISO, value=99999, error=...",
  "failedField": "ISO",
  "reason": "CameraAgent setProperty failed: key=ISO, value=99999, error=..."
}
```

---

### 3. 获取属性描述（候选值）

**接口：** `GET /property/desc?key={key}`

**功能：** 获取相机属性的当前值和所有候选值

**PowerShell 命令：**
```powershell
# 获取 ISO 候选值
Invoke-RestMethod -Uri "http://localhost:18080/property/desc?key=ISO" -Method Get | ConvertTo-Json

# 获取光圈候选值
Invoke-RestMethod -Uri "http://localhost:18080/property/desc?key=APERTURE" -Method Get | ConvertTo-Json

# 批量获取所有参数的候选值
$params = @("ISO", "WB", "PictureStyle", "ExposureComp", "APERTURE", "SHUTTER_SPEED", "METERING_MODE")
foreach ($param in $params) {
    $result = Invoke-RestMethod -Uri "http://localhost:18080/property/desc?key=$param" -Method Get
    if ($result.ok) {
        Write-Host "$param 当前值: $($result.current), 候选值数量: $($result.candidates.Length)" -ForegroundColor Cyan
    }
}
```

**curl.exe 命令：**
```bash
curl.exe http://localhost:18080/property/desc?key=ISO
curl.exe http://localhost:18080/property/desc?key=APERTURE
curl.exe http://localhost:18080/property/desc?key=SHUTTER_SPEED
```

**预期响应（成功）：**
```json
{
  "ok": true,
  "key": "ISO",
  "current": 104,
  "candidates": [0, 72, 75, 77, 80, 83, 85, 88, 91, 93, 96, 99, 101, 104, 107, 109, 112, 115, 117, 120, 123, 125, 128, 131, 133, 136, 139, 141, 144, 147, 149, 152]
}
```

---

### 4. 获取相机状态

**接口：** `GET /status`

**功能：** 获取 CameraAgent 和相机的状态信息

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:18080/status" -Method Get | ConvertTo-Json -Depth 5
```

**curl.exe 命令：**
```bash
curl.exe http://localhost:18080/status
```

**预期响应：**
```json
{
  "ok": true,
  "cameraConnected": true,
  "error": null,
  "sdkInitialized": true,
  "sessionOpened": true,
  "cameraThreadId": 3,
  "apartmentState": "STA",
  "queueLength": 0,
  "model": "Canon EOS R6",
  "serial": null,
  "previewRunning": false,
  "lastPreviewFrameAgeMs": 0,
  "lastPreviewFrameSize": 0,
  "lastLoopTickMs": 123,
  "currentJob": null
}
```

---

### 5. 拍照

**接口：** `POST /capture`

**功能：** 触发拍照并保存到指定路径

**PowerShell 命令：**
```powershell
# 指定保存路径
$body = @{
    targetFile = "D:\AICreama\booth\data\raw\test_photo.jpg"
    timeoutMs = 30000
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:18080/capture" -Method Post -Body $body -ContentType "application/json"

# 使用默认路径（自动生成文件名）
$body = @{
    targetFile = ""
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:18080/capture" -Method Post -Body $body -ContentType "application/json"
```

**curl.exe 命令：**
```bash
# 指定保存路径
curl.exe -X POST http://localhost:18080/capture -H "Content-Type: application/json" -d "{\"targetFile\":\"D:\\AICreama\\booth\\data\\raw\\test_photo.jpg\",\"timeoutMs\":30000}"

# 使用默认路径
curl.exe -X POST http://localhost:18080/capture -H "Content-Type: application/json" -d "{\"targetFile\":\"\"}"
```

**预期响应（成功）：**
```json
{
  "ok": true,
  "path": "D:\\AICreama\\booth\\data\\raw\\test_photo.jpg",
  "size": 5242880,
  "elapsedMs": 1422,
  "errorCode": 0,
  "error": null
}
```

---

## 完整工作流示例

### 工作流 1: 应用预设并验证效果

**步骤 1: 应用预设**
```powershell
$body = @{
    presetId = "preset_night_indoor"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**步骤 2: 验证参数已应用**
```powershell
# 检查 MVP 配置
$config = Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Get
Write-Host "Active Preset: $($config.activePresetId)"
Write-Host "ISO: $($config.params.iso)"
Write-Host "WB: $($config.params.whiteBalance)"

# 检查相机实际值
$iso = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=ISO" -Method Get
Write-Host "相机 ISO 实际值: $($iso.value)"
```

**步骤 3: 测试拍照**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/test-shot" -Method Post
```

---

### 工作流 2: 修改预设参数并应用

**步骤 1: 更新预设参数**
```powershell
$body = @{
    iso = 200
    whiteBalance = "DAYLIGHT"
    aperture = "F5.6"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_day_outdoor/params" -Method Put -Body $body -ContentType "application/json"
```

**步骤 2: 应用更新后的预设**
```powershell
$body = @{
    presetId = "preset_day_outdoor"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**步骤 3: 验证参数**
```powershell
# 检查相机实际值
$iso = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=ISO" -Method Get
$wb = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=WB" -Method Get
$aperture = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=APERTURE" -Method Get

Write-Host "ISO: $($iso.value) (期望: 80 = ISO 200)"
Write-Host "WB: $($wb.value) (期望: 1 = DAYLIGHT)"
Write-Host "Aperture: $($aperture.value) (期望: 40 = F5.6)"
```

---

### 工作流 3: 手动调整参数

**步骤 1: 应用预设（作为基础）**
```powershell
$body = @{
    presetId = "preset_day_outdoor"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**步骤 2: 手动调整参数**
```powershell
$body = @{
    iso = 400
    exposureCompensationEv = 0.3
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"
```

**步骤 3: 测试拍照**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/test-shot" -Method Post
```

---

## 错误处理示例

### 错误 1: 相机未连接

**请求：**
```bash
curl.exe http://localhost:8080/local/camera/status
```

**响应：**
```json
{
  "success": true,
  "data": {
    "ok": false,
    "cameraConnected": false,
    "error": "Camera not connected",
    ...
  },
  "message": null
}
```

**处理：**
1. 检查 CameraAgent 是否运行
2. 检查相机 USB 连接
3. 检查相机是否开机

---

### 错误 2: 参数值不支持

**请求：**
```bash
curl.exe -X POST http://localhost:8080/local/camera/apply-params -H "Content-Type: application/json" -d "{\"iso\":99999}"
```

**响应：**
```json
{
  "success": false,
  "data": {
    "applied": false,
    "failedFields": {
      "ISO": "CameraAgent setProperty failed: key=ISO, value=99999, error=Value 99999 is not in the candidate list"
    },
    "failedField": "ISO",
    "reason": "CameraAgent setProperty failed: key=ISO, value=99999, error=Value 99999 is not in the candidate list"
  },
  "message": "Some parameters failed to apply"
}
```

**处理：**
1. 查看 `failedField` 和 `reason` 了解具体错误
2. 通过 `/property/desc?key=ISO` 获取支持的候选值
3. 使用支持的候选值重新设置

---

### 错误 3: Preset 不存在

**请求：**
```bash
curl.exe -X POST http://localhost:8080/local/camera/presets/apply -H "Content-Type: application/json" -d "{\"presetId\":\"preset_not_exist\"}"
```

**响应：**
```json
{
  "success": false,
  "data": null,
  "message": "Preset not found: preset_not_exist"
}
```

**处理：**
1. 通过 `GET /local/camera/presets` 查看所有可用 preset
2. 使用正确的 presetId

---

### 错误 4: 非 localhost 访问

**请求：**
```bash
curl.exe http://192.168.1.100:8080/local/camera/config
```

**响应：**
```json
{
  "success": false,
  "message": "Access denied: only localhost allowed"
}
```

**HTTP 状态码：** 403 Forbidden

**处理：**
- 所有 `/local/camera/*` 接口只允许 localhost 访问
- 如需远程访问，需要修改 `isLocalhost()` 检查逻辑

---

## 参数值格式参考

### ISO
- **格式：** Integer
- **示例：** `100`, `200`, `400`, `800`, `1600`, `3200`, `6400`
- **特殊值：** `0` 表示 AUTO

### WhiteBalance
- **格式：** String
- **示例：** `"AUTO"`, `"DAYLIGHT"`, `"TUNGSTEN"`, `"FLUORESCENT"`, `"FLASH"`, `"KELVIN"`
- **大小写：** 不敏感（会自动转换为大写）

### ExposureCompensationEv
- **格式：** Double
- **范围：** -3.0 到 +3.0
- **步进：** 0.3
- **示例：** `-1.0`, `-0.3`, `0.0`, `0.3`, `1.0`

### PictureStyle
- **格式：** String
- **示例：** `"STANDARD"`, `"PORTRAIT"`, `"LANDSCAPE"`, `"NEUTRAL"`, `"FAITHFUL"`, `"MONOCHROME"`
- **大小写：** 不敏感

### Aperture
- **格式：** String（必须以 "F" 开头）
- **示例：** `"F2.8"`, `"F4.0"`, `"F5.6"`, `"F8.0"`
- **注意：** 如果输入 "2.8"，会自动转换为 "F2.8"

### ShutterSpeed
- **格式：** String
- **示例：** `"1/60"`, `"1/125"`, `"1/250"`, `"1/500"`
- **注意：** 支持分数格式（"1/60"）或小数格式（"0.5"）或整数格式（"2"）

### MeteringMode
- **格式：** String
- **示例：** `"EVALUATIVE"`, `"PARTIAL"`, `"SPOT"`, `"CENTER_WEIGHTED"`
- **大小写：** 不敏感

---

## 快速参考表

### MVP API 快速参考

| 方法 | 路径 | 功能 | 常用场景 |
|------|------|------|---------|
| GET | `/local/camera/config` | 获取配置 | 查看当前参数和预设 |
| PUT | `/local/camera/config` | 保存配置 | 批量更新配置 |
| GET | `/local/camera/status` | 获取状态 | 检查相机连接 |
| POST | `/local/camera/test-shot` | 测试拍照 | 验证参数效果 |
| POST | `/local/camera/apply-params` | 应用参数 | 手动调整参数 |
| POST | `/local/camera/presets/apply` | 应用预设 | 快速切换场景 |
| GET | `/local/camera/presets` | 获取预设列表 | 查看所有可用预设 |
| PUT | `/local/camera/presets/{id}/params` | 更新预设参数 | 修改预设定义 |

### CameraAgent API 快速参考

| 方法 | 路径 | 功能 | 常用场景 |
|------|------|------|---------|
| GET | `/property/get?key=ISO` | 获取属性值 | 查看相机实际值 |
| POST | `/property/set` | 设置属性值 | 直接设置（使用编码值） |
| GET | `/property/desc?key=ISO` | 获取候选值 | 查看支持的参数值 |
| GET | `/status` | 获取状态 | 诊断相机连接 |
| POST | `/capture` | 拍照 | 底层拍照接口 |

---

## 常见问题

### Q1: PowerShell 中 JSON 格式问题

**问题：** PowerShell 的 `ConvertTo-Json` 可能产生格式问题

**解决：** 使用 `-Depth` 参数或直接使用字符串：
```powershell
# 方法 1: 使用 -Depth
$body = @{ presetId = "preset_night_indoor" } | ConvertTo-Json -Depth 5

# 方法 2: 直接使用字符串
$body = '{"presetId":"preset_night_indoor"}'
```

---

### Q2: curl.exe 在 Windows 中的引号问题

**问题：** Windows `cmd` 中 JSON 引号需要转义

**解决：** 使用双引号并转义内部引号：
```bash
# 正确
curl.exe -X POST http://localhost:8080/local/camera/presets/apply -H "Content-Type: application/json" -d "{\"presetId\":\"preset_night_indoor\"}"

# 或使用文件
curl.exe -X POST http://localhost:8080/local/camera/presets/apply -H "Content-Type: application/json" -d @request.json
```

---

### Q3: 如何批量测试所有预设

**PowerShell 脚本：**
```powershell
# 获取所有预设
$presets = (Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets" -Method Get).data

foreach ($preset in $presets) {
    Write-Host "应用预设: $($preset.id) - $($preset.displayName)" -ForegroundColor Cyan
    
    $body = @{ presetId = $preset.id } | ConvertTo-Json
    $result = Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
    
    if ($result.success) {
        Write-Host "  ✅ 成功" -ForegroundColor Green
    } else {
        Write-Host "  ❌ 失败: $($result.message)" -ForegroundColor Red
    }
    
    Start-Sleep -Seconds 2
}
```

---

## 相关文档

- `CAMERA_PARAMS_VALUE_REFERENCE.md` - 参数值完整对照表
- `PHASE_C3_DEVELOPMENT_SUMMARY.md` - 开发总结文档
- `CAMERA_APPLY_PARAMS_TEST.md` - ApplyParams API 测试文档
- `BUSINESS_PRESETS_MERGE_TEST.md` - Business Presets 合并测试文档

---

**文档版本：** 1.0  
**最后更新：** 2026年1月  
**维护者：** AI Photo Booth Team
