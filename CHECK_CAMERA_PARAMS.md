# 查看相机底层实际参数值

## 方法 1: 使用 CameraAgent 的 /property/get 接口（推荐）

### 查看单个参数值

**ISO 感光度：**
```bash
curl.exe http://localhost:18080/property/get?key=ISO
```

**白平衡 (WB)：**
```bash
curl.exe http://localhost:18080/property/get?key=WB
```

**画面风格 (PictureStyle)：**
```bash
curl.exe http://localhost:18080/property/get?key=PictureStyle
```

**曝光补偿 (ExposureComp)：**
```bash
curl.exe http://localhost:18080/property/get?key=ExposureComp
```

**光圈 (APERTURE)：**
```bash
curl.exe http://localhost:18080/property/get?key=APERTURE
```

**快门速度 (SHUTTER_SPEED)：**
```bash
curl.exe http://localhost:18080/property/get?key=SHUTTER_SPEED
```

**测光模式 (METERING_MODE)：**
```bash
curl.exe http://localhost:18080/property/get?key=METERING_MODE
```

### PowerShell 批量查询所有参数

```powershell
$params = @("ISO", "WB", "PictureStyle", "ExposureComp", "APERTURE", "SHUTTER_SPEED", "METERING_MODE")

foreach ($param in $params) {
    $result = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=$param" -Method Get
    Write-Host "$param = $($result.value) (ok=$($result.ok))"
}
```

---

## 方法 2: 使用 /property/desc 接口（查看当前值和候选值）

这个接口会返回当前值和所有候选值，更详细：

**ISO 感光度：**
```bash
curl.exe http://localhost:18080/property/desc?key=ISO
```

**白平衡：**
```bash
curl.exe http://localhost:18080/property/desc?key=WB
```

**画面风格：**
```bash
curl.exe http://localhost:18080/property/desc?key=PictureStyle
```

**光圈：**
```bash
curl.exe http://localhost:18080/property/desc?key=APERTURE
```

**快门速度：**
```bash
curl.exe http://localhost:18080/property/desc?key=SHUTTER_SPEED
```

**测光模式：**
```bash
curl.exe http://localhost:18080/property/desc?key=METERING_MODE
```

### PowerShell 批量查询（带候选值）

```powershell
$params = @("ISO", "WB", "PictureStyle", "ExposureComp", "APERTURE", "SHUTTER_SPEED", "METERING_MODE")

foreach ($param in $params) {
    $result = Invoke-RestMethod -Uri "http://localhost:18080/property/desc?key=$param" -Method Get
    if ($result.ok) {
        Write-Host "$param = $($result.current) (候选值数量: $($result.candidates.Length))" -ForegroundColor Green
    } else {
        Write-Host "$param = 错误: $($result.error)" -ForegroundColor Red
    }
}
```

---

## 方法 3: 对比预设参数和实际值

### 步骤 1: 查看预设参数（期望值）

```bash
curl.exe http://localhost:8080/local/camera/config | findstr "preset_night_indoor" -A 20
```

### 步骤 2: 查看相机实际值（底层编码值）

使用上面的命令查看各个参数的实际值。

### 步骤 3: 对比

- **预设参数**：使用直观值（如 ISO=1600, WB="TUNGSTEN"）
- **相机实际值**：使用 EDSDK 编码值（如 ISO=104, WB=4）

需要将预设参数转换为编码值后对比，或者查看转换后的值是否匹配。

---

## 参数值对照表（参考）

### ISO 编码值对照
- `0` = AUTO
- `72` = ISO 100
- `80` = ISO 200
- `88` = ISO 400
- `96` = ISO 800
- `104` = ISO 1600
- `112` = ISO 3200
- `120` = ISO 6400

### 白平衡编码值对照
- `0` = AUTO
- `1` = DAYLIGHT
- `2` = SHADE
- `3` = CLOUDY
- `4` = TUNGSTEN
- `5` = FLUORESCENT
- `6` = FLASH

### 画面风格编码值对照
- `129` = STANDARD
- `130` = PORTRAIT
- `131` = LANDSCAPE
- `132` = NEUTRAL
- `133` = FAITHFUL
- `134` = MONOCHROME

### 光圈编码值对照（示例）
- `24` = F2.8
- `32` = F4.0
- `40` = F5.6
- `72` = F8.0

### 快门速度编码值对照（示例）
- `64` = 1/125s
- `72` = 1/250s
- `80` = 1/500s

### 测光模式编码值对照
- `1` = EVALUATIVE（评价测光）
- `3` = PARTIAL（局部测光）
- `4` = SPOT（点测光）
- `5` = CENTER_WEIGHTED（中央重点测光）

---

## 快速验证脚本（PowerShell）

```powershell
# 查看所有参数的实际值
Write-Host "=== 相机底层实际参数值 ===" -ForegroundColor Cyan

$params = @{
    "ISO" = "ISO 感光度"
    "WB" = "白平衡"
    "PictureStyle" = "画面风格"
    "ExposureComp" = "曝光补偿"
    "APERTURE" = "光圈"
    "SHUTTER_SPEED" = "快门速度"
    "METERING_MODE" = "测光模式"
}

foreach ($key in $params.Keys) {
    try {
        $result = Invoke-RestMethod -Uri "http://localhost:18080/property/get?key=$key" -Method Get
        if ($result.ok) {
            Write-Host "$($params[$key]) ($key) = $($result.value)" -ForegroundColor Green
        } else {
            Write-Host "$($params[$key]) ($key) = 错误: $($result.error)" -ForegroundColor Red
        }
    } catch {
        Write-Host "$($params[$key]) ($key) = 请求失败: $_" -ForegroundColor Red
    }
}
```

---

## 预期结果示例

应用 `preset_night_indoor` 后，预期值应该是：

```
ISO = 104 (对应 ISO 1600)
WB = 4 (对应 TUNGSTEN)
PictureStyle = 130 (对应 PORTRAIT)
ExposureComp = 2 (对应 +0.3 EV，需要根据实际编码计算)
APERTURE = 24 (对应 F2.8)
SHUTTER_SPEED = 48 (对应 1/60s，需要根据实际编码计算)
METERING_MODE = 5 (对应 CENTER_WEIGHTED)
```

**注意：** 实际编码值可能因相机型号而异，建议先查看 `/property/desc` 获取候选值列表。
