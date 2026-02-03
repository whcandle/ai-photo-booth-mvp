# Preset 应用验证结果分析

## preset_night_indoor 预设参数

根据代码定义：
- **ISO**: 1600
- **WhiteBalance**: "TUNGSTEN"
- **ExposureCompensationEv**: 0.3
- **PictureStyle**: "PORTRAIT"
- **Aperture**: "F2.8"
- **ShutterSpeed**: "1/60"
- **MeteringMode**: "CENTER_WEIGHTED"

---

## 实际相机值 vs 预设值对比

### ✅ 已生效的参数

| 参数 | 预设值 | 实际值 | 编码值对照 | 状态 |
|------|--------|--------|------------|------|
| **ISO** | 1600 | 104 | 104 = ISO 1600 | ✅ **生效** |
| **WB** | TUNGSTEN | 4 | 4 = TUNGSTEN | ✅ **生效** |
| **PictureStyle** | PORTRAIT | 130 | 130 = PORTRAIT | ✅ **生效** |

### ⚠️ 未生效的参数

| 参数 | 预设值 | 实际值 | 期望编码值 | 状态 |
|------|--------|--------|------------|------|
| **ExposureComp** | +0.3 EV | 0 | 应该是 2-3 (对应 +0.3 EV) | ❌ **未生效** |
| **APERTURE** | F2.8 | 32 | 应该是 24 (对应 F2.8) | ❌ **未生效** |
| **SHUTTER_SPEED** | 1/60s | 64 | 应该是 48 (对应 1/60s) | ❌ **未生效** |
| **METERING_MODE** | CENTER_WEIGHTED | 1 | 应该是 5 (对应 CENTER_WEIGHTED) | ❌ **未生效** |

---

## 原因分析

### 为什么部分参数未生效？

1. **ExposureComp、APERTURE、SHUTTER_SPEED、METERING_MODE 暂未实现转换**
   - 查看 `CameraParamsConverter.java`，目前只支持 4 个参数：
     - ✅ ISO
     - ✅ WB (WhiteBalance)
     - ✅ ExposureComp (但转换可能有问题)
     - ✅ PictureStyle
   - ❌ Aperture、ShutterSpeed、MeteringMode 暂不支持转换

2. **ExposureComp 转换问题**
   - 预设值是 `0.3` EV
   - 实际值是 `0` (0 EV)
   - 可能是转换逻辑有问题，或者相机不支持该值

---

## 验证方法

### 检查 CameraParamsConverter 的转换结果

查看 MVP 日志，应该能看到类似：
```
[params-converter] Aperture is not supported yet: F2.8
[params-converter] ShutterSpeed is not supported yet: 1/60
[params-converter] MeteringMode is not supported yet: CENTER_WEIGHTED
```

这说明这些参数没有被转换，所以没有应用到相机。

---

## 解决方案

### 方案 1: 扩展 CameraParamsConverter（推荐）

需要在 `CameraParamsConverter.java` 中添加：
1. **Aperture 转换**：将 "F2.8" → 24, "F4.0" → 32 等
2. **ShutterSpeed 转换**：将 "1/60" → 48, "1/125" → 64 等
3. **MeteringMode 转换**：将 "CENTER_WEIGHTED" → 5, "EVALUATIVE" → 1 等
4. **修复 ExposureComp 转换**：确保 0.3 EV 正确转换为编码值

### 方案 2: 临时手动设置（测试用）

如果需要立即测试，可以手动设置这些参数：

```bash
# 设置光圈 F2.8 (编码值 24)
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"APERTURE\",\"Value\":24,\"Persist\":false}"

# 设置快门 1/60s (编码值 48)
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"SHUTTER_SPEED\",\"Value\":48,\"Persist\":false}"

# 设置测光模式 CENTER_WEIGHTED (编码值 5)
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"METERING_MODE\",\"Value\":5,\"Persist\":false}"

# 设置曝光补偿 +0.3 EV (编码值 2-3，需要根据实际计算)
curl.exe -X POST http://localhost:18080/property/set -H "Content-Type: application/json" -d "{\"Key\":\"ExposureComp\",\"Value\":2,\"Persist\":false}"
```

---

## 总结

**当前状态：**
- ✅ **3 个参数已生效**：ISO (1600), WB (TUNGSTEN), PictureStyle (PORTRAIT)
- ❌ **4 个参数未生效**：ExposureComp, Aperture, ShutterSpeed, MeteringMode

**原因：**
- Aperture、ShutterSpeed、MeteringMode 在 `CameraParamsConverter` 中暂未实现转换
- ExposureComp 的转换可能有问题

**下一步：**
- 需要扩展 `CameraParamsConverter` 支持这 4 个参数的转换
- 或者先手动设置这些参数进行测试
