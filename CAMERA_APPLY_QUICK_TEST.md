# Camera Apply Params + Apply Preset 快速测试指南

## 快速验收（3 步）

### 步骤 1: 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

### 步骤 2: 测试应用预设
```bash
curl -X POST http://localhost:8080/local/camera/presets/apply ^
  -H "Content-Type: application/json" ^
  -d "{\"presetId\":\"preset_night_indoor\"}"
```

**预期：** 返回 `{"success":true,"data":{"applied":true}}`

### 步骤 3: 测试拍照验证效果
```bash
curl -X POST http://localhost:8080/local/camera/test-shot
```

**预期：** 返回文件路径，照片曝光/色温有明显变化

**验证持久化：**
```bash
# 检查 camera.json 是否已更新
curl http://localhost:8080/local/camera/config | findstr "activePresetId"
# 应该显示：preset_night_indoor
```

---

## 验收标准

✅ **apply preset → test-shot，画面曝光/色温变化明显**  
✅ **错误时响应包含 failedField 和 reason**  
✅ **应用成功后，camera.json 已更新**

---

## 详细测试文档

完整测试步骤请参考：`CAMERA_APPLY_PARAMS_TEST.md`
