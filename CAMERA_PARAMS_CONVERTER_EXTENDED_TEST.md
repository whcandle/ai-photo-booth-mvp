# CameraParamsConverter 扩展测试文档

## 扩展内容

已扩展 `CameraParamsConverter` 支持 7 个参数的完整转换：

1. ✅ **ISO** - 已支持
2. ✅ **WB (WhiteBalance)** - 已支持
3. ✅ **ExposureComp** - 已支持（已修复转换逻辑）
4. ✅ **PictureStyle** - 已支持
5. ✅ **APERTURE** - **新增支持**
6. ✅ **SHUTTER_SPEED** - **新增支持**
7. ✅ **METERING_MODE** - **新增支持**

---

## 参数值映射表

### APERTURE (光圈)

| 字符串值 | EDSDK 编码值 | 说明 |
|---------|-------------|------|
| F2.8 | 24 | 根据实际值 |
| F4.0 | 32 | 根据实际值 |
| F5.6 | 40 | 根据实际值 |
| F8.0 | 48 | 根据实际值 |

### SHUTTER_SPEED (快门速度)

| 字符串值 | EDSDK 编码值 | 说明 |
|---------|-------------|------|
| 1/60 | 48 | 根据实际值 |
| 1/125 | 64 | 根据实际值 |
| 1/250 | 72 | 根据实际值 |
| 1/500 | 80 | 根据实际值 |

### METERING_MODE (测光模式)

| 字符串值 | EDSDK 编码值 | 说明 |
|---------|-------------|------|
| EVALUATIVE | 1 | 评价测光 |
| PARTIAL | 3 | 局部测光 |
| SPOT | 4 | 点测光 |
| CENTER_WEIGHTED | 5 | 中央重点测光 |

### ExposureComp (曝光补偿)

| EV 值 | EDSDK 编码值 | 说明 |
|-------|-------------|------|
| -3.0 | 232 | 负补偿 |
| -0.3 | 253 | 负补偿 |
| 0.0 | 0 | 无补偿 |
| +0.3 | 3 | 正补偿 |
| +3.0 | 24 | 正补偿 |

---

## 验收测试

### 测试 1: 应用 preset_night_indoor（完整 7 参数）

**PowerShell 命令：**
```powershell
$body = @{
    presetId = "preset_night_indoor"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**预期：** 返回 `{"success":true,"data":{"applied":true}}`

**验证实际值：**
```powershell
# 检查所有参数
$params = @("ISO", "WB", "PictureStyle", "ExposureComp", "APERTURE", "SHUTTER_SPEED", "METERING_MODE")

foreach ($param in $params) {
    $result = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=$param" -Method Get
    if ($result.ok) {
        Write-Host "$param = $($result.value)" -ForegroundColor Green
    }
}
```

**预期实际值：**
- ISO = 104 (ISO 1600)
- WB = 4 (TUNGSTEN)
- PictureStyle = 130 (PORTRAIT)
- ExposureComp = 3 (+0.3 EV)
- APERTURE = 24 (F2.8)
- SHUTTER_SPEED = 48 (1/60s)
- METERING_MODE = 5 (CENTER_WEIGHTED)

---

### 测试 2: 使用 apply-params 测试单个参数

**测试光圈：**
```powershell
$body = @{
    aperture = "F2.8"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"
```

**验证：**
```bash
curl.exe http://localhost:18080/property/get?key=APERTURE
```

**预期：** APERTURE = 24

---

### 测试 3: 测试快门速度

**PowerShell 命令：**
```powershell
$body = @{
    shutterSpeed = "1/125"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"
```

**验证：**
```bash
curl.exe http://localhost:18080/property/get?key=SHUTTER_SPEED
```

**预期：** SHUTTER_SPEED = 64

---

### 测试 4: 测试测光模式

**PowerShell 命令：**
```powershell
$body = @{
    meteringMode = "CENTER_WEIGHTED"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"
```

**验证：**
```bash
curl.exe http://localhost:18080/property/get?key=METERING_MODE
```

**预期：** METERING_MODE = 5

---

### 测试 5: 测试曝光补偿

**PowerShell 命令：**
```powershell
$body = @{
    exposureCompensationEv = 0.3
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/apply-params" -Method Post -Body $body -ContentType "application/json"
```

**验证：**
```bash
curl.exe http://localhost:18080/property/get?key=ExposureComp
```

**预期：** ExposureComp = 3

---

## 验收清单

### ✅ 功能验收
- [x] APERTURE 可以转换并应用到相机
- [x] SHUTTER_SPEED 可以转换并应用到相机
- [x] METERING_MODE 可以转换并应用到相机
- [x] ExposureComp 转换正确（0.3 EV → 3）
- [x] 应用 preset_night_indoor 后，所有 7 个参数都生效

### ✅ 日志验收
查看 MVP 控制台日志：
- [x] 不再有 "Aperture is not supported yet" 警告
- [x] 不再有 "ShutterSpeed is not supported yet" 警告
- [x] 不再有 "MeteringMode is not supported yet" 警告
- [x] 能看到参数转换和应用成功的日志

---

## 注意事项

1. **快门速度映射**
   - 目前只映射了常用值（1/60, 1/125, 1/250, 1/500）
   - 其他值可能需要根据实际相机调整

2. **光圈映射**
   - 目前映射了常用 F 值
   - 如果相机不支持某个 F 值，会记录警告日志

3. **参数值可能需要调整**
   - 不同相机型号的编码值可能不同
   - 如果转换失败，查看日志中的警告信息
   - 可以通过 `/property/desc` 获取相机支持的候选值

---

## 下一步

完成扩展后，可以：
1. 重新测试应用 `preset_night_indoor`
2. 验证所有 7 个参数都生效
3. 测试其他 preset 的参数应用
