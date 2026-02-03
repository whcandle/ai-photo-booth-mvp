# Business Presets 合并快速测试指南

## 快速验收（4 步）

### 步骤 1: 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

### 步骤 2: 获取预设列表（验证 4 个 business presets 存在）

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets" -Method Get | ConvertTo-Json -Depth 10
```

**预期：** 返回的 `data` 数组中包含 4 个 `preset_business_*` 预设

### 步骤 3: 应用业务场景预设

**PowerShell 命令：**
```powershell
$body = @{
    presetId = "preset_business_idphoto"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**预期：** 返回 `{"success":true,"data":{"applied":true}}`

### 步骤 4: 验证持久化和效果

**验证 activePresetId：**
```powershell
$config = Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Get
$config.activePresetId
```

**预期：** 显示 `preset_business_idphoto`

**测试拍照：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/test-shot" -Method Post
```

**预期：** 返回文件路径，照片画面风格/曝光有明显变化

---

## 验收标准

✅ **GET /local/camera/presets 能看到 4 个新 presetId**  
✅ **POST /local/camera/presets/apply 返回 success=true**  
✅ **GET /local/camera/config 中 activePresetId 已更新**  
✅ **POST /local/camera/test-shot 后画面风格/曝光有变化**

---

## 详细测试文档

完整测试步骤请参考：`BUSINESS_PRESETS_MERGE_TEST.md`
