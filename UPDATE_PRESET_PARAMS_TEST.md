# 更新 Preset 参数 API 测试文档

## 新增接口

**PUT /local/camera/presets/{presetId}/params**

用于更新指定 preset 的参数定义（永久修改）。

---

## 功能说明

1. **更新 preset 的参数定义**
   - 修改 `camera.json` 中指定 preset 的 `params` 字段
   - 只更新提供的字段，未提供的字段保持不变
   - 更新后保存到 `camera.json`

2. **限制**
   - 只允许 localhost 访问
   - 不允许修改 legacy preset（有 `legacyProfileId` 的 preset）的参数

3. **参数格式**
   - 使用直观值（可读字符串/数值），不是底层编码值
   - 例如：ISO 传 `200`，白平衡传 `"DAYLIGHT"`

---

## 验收测试

### 准备工作

1. **启动 MVP**
   ```bash
   cd d:\workspace\ai-photo-booth-mvp
   mvn spring-boot:run
   ```

2. **确认相机服务**
   - 确保 CameraAgent 服务已启动（默认端口 18080）
   - 确保相机已连接

---

### 测试 1: 更新 preset_day_outdoor 的参数

**PowerShell 命令：**
```powershell
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
```

**或者使用 curl.exe：**
```bash
curl.exe -X PUT http://localhost:8080/local/camera/presets/preset_day_outdoor/params -H "Content-Type: application/json" -d "{\"iso\":200,\"whiteBalance\":\"DAYLIGHT\",\"exposureCompensationEv\":0.3,\"pictureStyle\":\"PORTRAIT\",\"aperture\":\"F5.6\",\"shutterSpeed\":\"1/500\",\"meteringMode\":\"CENTER_WEIGHTED\"}"
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

**预期日志：**
```
[camera-update-preset-params] Updating preset params: presetId=preset_day_outdoor, iso=200, wb=DAYLIGHT, ev=0.3, style=PORTRAIT
[camera-update-preset-params] Preset params updated successfully: presetId=preset_day_outdoor
```

**验证更新：**
```bash
curl http://localhost:8080/local/camera/config | findstr "preset_day_outdoor" -A 10
```

应该能看到更新后的参数。

---

### 测试 2: 部分更新（只更新 ISO 和 WhiteBalance）

**PowerShell 命令：**
```powershell
$body = @{
    iso = 400
    whiteBalance = "FLUORESCENT"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_day_outdoor/params" -Method Put -Body $body -ContentType "application/json"
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "presetId": "preset_day_outdoor",
    "updatedParams": {
      "iso": 400,
      "whiteBalance": "FLUORESCENT",
      "exposureCompensationEv": 0.3,  // 保持之前的值
      "pictureStyle": "PORTRAIT",      // 保持之前的值
      ...
    }
  },
  "message": null
}
```

**验收点：**
- ✅ 只更新提供的字段（ISO 和 WhiteBalance）
- ✅ 其他字段保持不变

---

### 测试 3: 验证更新后的 preset 应用

**步骤 1：应用更新后的 preset**
```bash
curl -X POST http://localhost:8080/local/camera/presets/apply -H "Content-Type: application/json" -d "{\"presetId\":\"preset_day_outdoor\"}"
```

**步骤 2：验证当前参数**
```bash
curl http://localhost:8080/local/camera/config | findstr "params" -A 5
```

**预期：** `camera.json.params` 应该与更新后的 preset 参数一致。

---

### 测试 4: 错误场景 - preset 不存在

**PowerShell 命令：**
```powershell
$body = @{
    iso = 200
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_not_exist/params" -Method Put -Body $body -ContentType "application/json"
```

**预期响应：**
```json
{
  "success": false,
  "data": null,
  "message": "Preset not found: preset_not_exist"
}
```

---

### 测试 5: 错误场景 - 尝试修改 legacy preset

**PowerShell 命令：**
```powershell
$body = @{
    iso = 200
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_business_idphoto/params" -Method Put -Body $body -ContentType "application/json"
```

**预期响应：**
```json
{
  "success": false,
  "data": null,
  "message": "Cannot update params for legacy preset (preset uses legacyProfileId). PresetId: preset_business_idphoto"
}
```

**验收点：**
- ✅ 不允许修改 legacy preset 的参数
- ✅ 返回明确的错误信息

---

## 验收清单

### ✅ 功能验收
- [x] PUT /local/camera/presets/{presetId}/params 可以更新 preset 参数
- [x] 只更新提供的字段，未提供的字段保持不变
- [x] 更新后保存到 camera.json
- [x] 应用更新后的 preset 时，使用新的参数值
- [x] preset 不存在时返回明确错误
- [x] legacy preset 不允许修改参数

### ✅ 日志验收
查看 MVP 控制台日志：
- [x] 更新请求：`[camera-update-preset-params] Updating preset params: presetId=..., iso=..., wb=...`
- [x] 更新成功：`[camera-update-preset-params] Preset params updated successfully: presetId=...`

---

## 支持的参数值

### ISO
- 支持值：`100`, `200`, `400`, `800`, `1600`, `3200`, `6400`（Integer）
- 或 `0` 表示 AUTO

### WhiteBalance
- 支持值：`"AUTO"`, `"DAYLIGHT"`, `"SHADE"`, `"CLOUDY"`, `"TUNGSTEN"`, `"FLUORESCENT"`, `"FLASH"`, `"KELVIN"`（String）

### ExposureCompensationEv
- 支持值：`-3.0` 到 `+3.0`，步进 `0.3`（Double）
- 例如：`-1.0`, `-0.7`, `-0.3`, `0.0`, `0.3`, `0.7`, `1.0`

### PictureStyle
- 支持值：`"STANDARD"`, `"PORTRAIT"`, `"LANDSCAPE"`, `"NEUTRAL"`, `"FAITHFUL"`, `"MONOCHROME"`（String）

### Aperture
- 支持值：字符串格式，如 `"F2.8"`, `"F4.0"`, `"F5.6"`, `"F8.0"`（String）
- 注意：目前 CameraControl 已支持，但 CameraParamsConverter 暂未实现转换

### ShutterSpeed
- 支持值：字符串格式，如 `"1/60"`, `"1/125"`, `"1/250"`, `"1/500"`（String）
- 注意：目前 CameraControl 已支持，但 CameraParamsConverter 暂未实现转换

### MeteringMode
- 支持值：`"EVALUATIVE"`, `"PARTIAL"`, `"SPOT"`, `"CENTER_WEIGHTED"`（String）
- 注意：目前 CameraControl 已支持，但 CameraParamsConverter 暂未实现转换

---

## 注意事项

1. **参数值格式**
   - 使用直观值（可读字符串/数值），不是底层编码值
   - MVP 内部会自动转换为 EDSDK 编码值

2. **Legacy Preset 限制**
   - 不允许修改有 `legacyProfileId` 的 preset 参数
   - 这些 preset 的参数由旧的 CameraProfileService 控制

3. **部分更新**
   - 只提供需要修改的字段即可
   - 未提供的字段会保持原值不变

4. **持久化**
   - 更新后立即保存到 `camera.json`
   - 下次应用该 preset 时会使用新的参数值
