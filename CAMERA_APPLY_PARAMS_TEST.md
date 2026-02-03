# Camera Apply Params + Apply Preset API 测试验收文档

## Phase C3: ApplyParams + ApplyPreset 验收

### 1. 准备工作

#### 1.1 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

#### 1.2 确认相机服务
- 确保 CameraAgent 服务已启动（默认端口 18080）
- 确保相机已连接

---

## 2. 验收测试

### 2.1 测试 POST /local/camera/presets/apply（应用预设套餐）

**命令：**
```bash
curl -X POST http://localhost:8080/local/camera/presets/apply ^
  -H "Content-Type: application/json" ^
  -d "{\"presetId\":\"preset_night_indoor\"}"
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

**预期响应（部分失败）：**
```json
{
  "success": false,
  "data": {
    "applied": false,
    "failedFields": {
      "ISO": "Unsupported value: 1600"
    },
    "failedField": "ISO",
    "reason": "Unsupported value: 1600"
  },
  "message": "Some parameters failed to apply"
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ 成功时，`data.applied = true`
- ✅ 失败时，`data.failedField` 和 `data.reason` 包含明确的错误信息
- ✅ 成功后，`camera.json` 中的 `params` 和 `activePresetId` 已更新

**验证持久化：**
```bash
# 应用预设后，检查 camera.json
curl http://localhost:8080/local/camera/config | findstr "activePresetId"
# 应该显示新的 presetId

curl http://localhost:8080/local/camera/config | findstr "iso"
# 应该显示预设中的 ISO 值
```

---

### 2.2 测试 POST /local/camera/apply-params（应用参数，部分更新）

**命令：**
```bash
curl -X POST http://localhost:8080/local/camera/apply-params ^
  -H "Content-Type: application/json" ^
  -d "{\"iso\":200,\"whiteBalance\":\"DAYLIGHT\",\"exposureCompensationEv\":0.3}"
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
      "ISO": "Unsupported value: 200"
    },
    "failedField": "ISO",
    "reason": "Unsupported value: 200"
  },
  "message": "Some parameters failed to apply"
}
```

**验收点：**
- ✅ 返回 HTTP 200
- ✅ 成功时，`data.applied = true`
- ✅ 只更新提供的参数，其他参数保持不变
- ✅ 失败时，`data.failedField` 和 `data.reason` 包含明确的错误信息
- ✅ 成功后，`camera.json` 中的 `params` 已更新

**验证部分更新：**
```bash
# 1. 先获取当前配置
curl http://localhost:8080/local/camera/config -o before.json

# 2. 只更新 ISO
curl -X POST http://localhost:8080/local/camera/apply-params ^
  -H "Content-Type: application/json" ^
  -d "{\"iso\":400}"

# 3. 再次获取配置
curl http://localhost:8080/local/camera/config -o after.json

# 4. 对比：ISO 应该变为 400，其他参数应该保持不变
```

---

### 2.3 测试完整流程（应用预设 → 测试拍照）

**步骤 1：应用预设**
```bash
curl -X POST http://localhost:8080/local/camera/presets/apply ^
  -H "Content-Type: application/json" ^
  -d "{\"presetId\":\"preset_night_indoor\"}"
```

**步骤 2：测试拍照**
```bash
curl -X POST http://localhost:8080/local/camera/test-shot
```

**步骤 3：应用另一个预设**
```bash
curl -X POST http://localhost:8080/local/camera/presets/apply ^
  -H "Content-Type: application/json" ^
  -d "{\"presetId\":\"preset_day_outdoor\"}"
```

**步骤 4：再次测试拍照**
```bash
curl -X POST http://localhost:8080/local/camera/test-shot
```

**验收点：**
- ✅ 两张照片的曝光/色温有明显差异（至少 ISO/白平衡能看出来）
- ✅ 预设参数确实应用到相机

---

### 2.4 测试错误处理（不支持的参数值）

**命令（使用不支持的 ISO 值）：**
```bash
curl -X POST http://localhost:8080/local/camera/apply-params ^
  -H "Content-Type: application/json" ^
  -d "{\"iso\":99999}"
```

**预期响应：**
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

**验收点：**
- ✅ 返回明确的错误信息
- ✅ `failedField` 指出哪个参数失败
- ✅ `reason` 包含详细的失败原因

---

## 3. 验收清单

### ✅ 功能验收
- [x] POST /local/camera/presets/apply 成功应用预设
- [x] POST /local/camera/apply-params 成功应用参数（部分更新）
- [x] 应用成功后，`camera.json.params` 已更新
- [x] 应用预设后，`camera.json.activePresetId` 已更新
- [x] 错误时返回 `failedField` 和 `reason`
- [x] apply preset → test-shot，画面曝光/色温变化明显

### ✅ 日志验收
启动 MVP 后，查看控制台日志：
- [x] 应用前：`[camera-apply-preset] Applying preset: presetId=...`
- [x] 应用后：`[camera-apply-preset] Preset applied successfully and saved to camera.json`
- [x] 失败原因：`[camera-apply-preset] Failed to apply property ISO = ...: ...`

---

## 4. 常见问题

### Q1: 应用预设后，相机参数没有变化
**原因：** 参数值转换失败或相机不支持该值  
**解决：** 
- 查看日志中的警告信息
- 检查 `CameraParamsConverter` 中的映射值是否正确
- 确认相机是否支持该参数值

### Q2: Aperture、ShutterSpeed、MeteringMode 没有应用
**原因：** 这些参数暂不支持（需要 CameraControl 扩展）  
**解决：** 这是预期的，目前只支持 ISO、WB、ExposureComp、PictureStyle 四个参数

### Q3: 应用参数后，camera.json 没有更新
**原因：** 可能有参数应用失败，导致没有持久化  
**解决：** 查看日志和响应中的 `failedFields`

---

## 5. 关于场景策略的说明

### 现有系统（CameraProfileService）
- 4 个业务场景：医疗/养老、证件照/工牌、展会/活动、养老记录/家属留存
- 使用 EDSDK 编码值（Integer）
- 通过 `/api/v1/camera/profiles/{id}/apply` 应用

### 新系统（CameraConfig）
- 4 个环境预设：白天/夜晚 × 室内/室外
- 使用可读字符串值
- 通过 `/local/camera/presets/apply` 应用

### 建议
- **短期**：两个系统并存，互不干扰
- **长期**：将业务场景迁移到新的 preset 系统，或两者合并（业务场景 + 环境预设）

---

## 6. 下一步

完成 Phase C3 验收后，可以继续：
- Phase C4: Kiosk Settings 页面 + CameraTab（只读展示）
- Phase C5: Kiosk 支持"应用套餐/应用参数/测试拍照"
