# 更新 Preset 参数 API 快速测试指南

## 新增接口

**PUT /local/camera/presets/{presetId}/params**

用于更新指定 preset 的参数定义（永久修改）。

---

## 快速测试（3 步）

### 步骤 1: 更新 preset_day_outdoor 的参数

**PowerShell 命令：**
```powershell
$body = @{
    iso = 200
    whiteBalance = "DAYLIGHT"
    exposureCompensationEv = 0.3
    pictureStyle = "PORTRAIT"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/preset_day_outdoor/params" -Method Put -Body $body -ContentType "application/json"
```

**预期：** 返回 `{"success":true,"data":{"presetId":"preset_day_outdoor",...}}`

### 步骤 2: 验证更新

**PowerShell 命令：**
```powershell
$config = Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Get
$preset = $config.presets | Where-Object { $_.id -eq "preset_day_outdoor" }
$preset.params
```

**预期：** 显示更新后的参数值

### 步骤 3: 应用更新后的 preset 验证效果

**PowerShell 命令：**
```powershell
$body = @{
    presetId = "preset_day_outdoor"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**预期：** 返回 `{"success":true,"data":{"applied":true}}`

---

## 验收标准

✅ **PUT /local/camera/presets/{presetId}/params 可以更新 preset 参数**  
✅ **只更新提供的字段，未提供的字段保持不变**  
✅ **更新后保存到 camera.json**  
✅ **应用更新后的 preset 时，使用新的参数值**

---

## 支持的参数值

- **ISO**: `100`, `200`, `400`, `800`, `1600`, `3200`, `6400`（或 `0` 表示 AUTO）
- **WhiteBalance**: `"AUTO"`, `"DAYLIGHT"`, `"SHADE"`, `"CLOUDY"`, `"TUNGSTEN"`, `"FLUORESCENT"`, `"FLASH"`, `"KELVIN"`
- **ExposureCompensationEv**: `-3.0` 到 `+3.0`，步进 `0.3`
- **PictureStyle**: `"STANDARD"`, `"PORTRAIT"`, `"LANDSCAPE"`, `"NEUTRAL"`, `"FAITHFUL"`, `"MONOCHROME"`
- **Aperture**: `"F2.8"`, `"F4.0"`, `"F5.6"`, `"F8.0"`（暂未实现转换）
- **ShutterSpeed**: `"1/60"`, `"1/125"`, `"1/250"`, `"1/500"`（暂未实现转换）
- **MeteringMode**: `"EVALUATIVE"`, `"PARTIAL"`, `"SPOT"`, `"CENTER_WEIGHTED"`（暂未实现转换）

---

## 注意事项

1. **不允许修改 legacy preset**（有 `legacyProfileId` 的 preset）
2. **使用直观值**（可读字符串/数值），不是底层编码值
3. **部分更新**：只提供需要修改的字段即可

---

## 详细测试文档

完整测试步骤请参考：`UPDATE_PRESET_PARAMS_TEST.md`
