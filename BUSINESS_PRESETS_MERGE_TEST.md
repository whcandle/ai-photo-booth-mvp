# Business Presets 合并验收文档

## Step C3: 合并旧的 4 个业务场景到新的 preset 系统

### 实现内容

1. **扩展 CameraPreset 类**
   - 添加 `legacyProfileId` 字段（映射到旧 CameraProfileService 的 profile ID）
   - 添加 `displayName` 字段（中文显示名称）
   - 添加 `category` 字段（BUSINESS 或 ENV）

2. **新增 4 个 business presets**
   - `preset_business_medical` → `medical_standard`（医疗/养老标准记录）
   - `preset_business_idphoto` → `id_photo`（证件照/工牌）
   - `preset_business_expo_pretty` → `event_marketing`（展会/活动讨喜）
   - `preset_business_family_archive` → `elder_care`（养老记录/家属留存）

3. **扩展 POST /local/camera/presets/apply**
   - 如果 preset 有 `legacyProfileId`，则调用 `CameraProfileService.apply()`
   - 成功后更新 `camera.json.activePresetId`

4. **新增 GET /local/camera/presets**
   - 返回所有 presets（含新 business presets）
   - 字段包含：id, displayName, category, tags, legacyProfileId, paramsPreview

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

### 测试 1: 获取预设列表（验证 4 个 business presets 存在）

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets" -Method Get
```

**或者使用 curl.exe：**
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
      "paramsPreview": { ... }
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
    {
      "id": "preset_business_idphoto",
      "name": "证件照/工牌",
      "displayName": "证件照/工牌",
      "category": "BUSINESS",
      "tags": ["business", "id", "photo"],
      "legacyProfileId": "id_photo",
      "paramsPreview": { ... }
    },
    {
      "id": "preset_business_expo_pretty",
      "name": "展会/活动讨喜",
      "displayName": "展会/活动讨喜",
      "category": "BUSINESS",
      "tags": ["business", "event", "marketing"],
      "legacyProfileId": "event_marketing",
      "paramsPreview": { ... }
    },
    {
      "id": "preset_business_family_archive",
      "name": "养老记录/家属留存",
      "displayName": "养老记录/家属留存",
      "category": "BUSINESS",
      "tags": ["business", "elder", "family"],
      "legacyProfileId": "elder_care",
      "paramsPreview": { ... }
    },
    ...
  ],
  "message": null
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ `data` 数组中包含 4 个 `preset_business_*` 预设
- ✅ 每个 business preset 的 `category` 为 `"BUSINESS"`
- ✅ 每个 business preset 的 `legacyProfileId` 不为 null

---

### 测试 2: 应用业务场景预设（新接口）

**PowerShell 命令：**
```powershell
$body = @{
    presetId = "preset_business_idphoto"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**或者使用 curl.exe：**
```bash
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

**预期日志：**
```
[camera-apply-preset] Applying preset: presetId=preset_business_idphoto
[camera-apply-preset] Found preset: name=证件照/工牌, legacyProfileId=id_photo
[camera-apply-preset] Using legacy profile branch: legacyProfileId=id_photo
[camera-apply-preset] Legacy profile apply result: success=true, failedProps={}
[camera-apply-preset] Legacy profile applied successfully, camera.json updated: activePresetId=preset_business_idphoto
```

**验收点：**
- ✅ 返回 `success=true`
- ✅ 日志显示 "Using legacy profile branch"
- ✅ 日志显示 "Legacy profile applied successfully"

---

### 测试 3: 验证持久化（activePresetId 已更新）

**PowerShell 命令：**
```powershell
$config = Invoke-RestMethod -Uri "http://localhost:8080/local/camera/config" -Method Get
$config.activePresetId
```

**或者使用 curl.exe：**
```bash
curl.exe http://localhost:8080/local/camera/config | findstr "activePresetId"
```

**预期响应：**
```json
{
  ...
  "activePresetId": "preset_business_idphoto",
  ...
}
```

**验收点：**
- ✅ `activePresetId` 已更新为 `preset_business_idphoto`

---

### 测试 4: 测试拍照验证效果

**PowerShell 命令：**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/local/camera/test-shot" -Method Post
```

**或者使用 curl.exe：**
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

**验收点：**
- ✅ 返回文件路径
- ✅ 照片画面风格/曝光有明显变化（至少和默认明显不同）

---

### 测试 5: 测试其他业务场景预设

**测试医疗/养老标准记录：**
```powershell
$body = @{
    presetId = "preset_business_medical"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**测试展会/活动讨喜：**
```powershell
$body = @{
    presetId = "preset_business_expo_pretty"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**测试养老记录/家属留存：**
```powershell
$body = @{
    presetId = "preset_business_family_archive"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/local/camera/presets/apply" -Method Post -Body $body -ContentType "application/json"
```

**验收点：**
- ✅ 每个业务场景预设都能成功应用
- ✅ `activePresetId` 正确更新

---

## 验收清单

### ✅ 功能验收
- [x] GET /local/camera/presets 能看到 4 个新 presetId
- [x] POST /local/camera/presets/apply {"presetId":"preset_business_idphoto"} 返回 success=true
- [x] GET /local/camera/config 中 activePresetId 已变为 preset_business_idphoto
- [x] POST /local/camera/test-shot 后画面风格/曝光有变化（至少和默认明显不同）
- [x] 所有 4 个业务场景预设都能成功应用

### ✅ 日志验收
查看 MVP 控制台日志：
- [x] apply preset 请求入参：`[camera-apply-preset] Applying preset: presetId=...`
- [x] 判断走 legacyProfileId 分支：`[camera-apply-preset] Using legacy profile branch: legacyProfileId=...`
- [x] 调用旧 profile apply 的结果：`[camera-apply-preset] Legacy profile apply result: success=..., failedProps=...`
- [x] camera.json 更新后的 activePresetId：`[camera-apply-preset] Legacy profile applied successfully, camera.json updated: activePresetId=...`

---

## 注意事项

1. **旧接口保留**
   - `/api/v1/camera/profiles/{id}/apply` 仍然可用
   - 两套系统并存，但 UI 只用新接口

2. **参数更新**
   - 使用 legacyProfileId 时，`camera.json.params` 会使用 preset 的占位参数
   - 实际相机参数由旧的 CameraProfileService 控制

3. **错误处理**
   - 如果 legacyProfileId 不存在，会返回明确的错误信息
   - 如果旧 profile apply 失败，会返回 failedFields 和 reason

---

## 下一步

完成 Step C3 验收后，可以继续：
- **Kiosk Settings 页面**：使用 `GET /local/camera/presets` 获取所有预设，使用 `POST /local/camera/presets/apply` 应用预设
- **不再需要调用旧接口** `/api/v1/camera/profiles/{id}/apply`
