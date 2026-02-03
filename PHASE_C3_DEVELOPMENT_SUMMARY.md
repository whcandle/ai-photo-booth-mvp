# Phase C3 开发总结文档

## 📋 项目概述

**项目名称：** AI Photo Booth MVP - 相机参数管理与预设系统  
**开发阶段：** Phase C3 - ApplyParams + ApplyPreset + Business Presets 合并  
**开发时间：** 2026年1月  
**技术栈：** Spring Boot (Java), C# (CameraControl), EDSDK (Canon SDK)

---

## 🎯 解决的问题

### 核心问题

1. **相机参数管理分散**
   - 旧系统使用 EDSDK 编码值（Integer），不直观
   - 新系统需要支持可读字符串值（如 "DAYLIGHT", "F2.8"）
   - 需要统一的参数转换机制

2. **业务场景与环境预设分离**
   - 旧的 4 个业务场景（医疗、证件照、展会、养老）使用旧接口
   - 新的 4 个环境预设（白天/夜晚 × 室内/室外）使用新接口
   - Kiosk/Settings 需要统一接口，避免维护两套系统

3. **参数支持不完整**
   - 初始只支持 4 个参数（ISO、WB、ExposureComp、PictureStyle）
   - 需要扩展到 7 个参数（增加 Aperture、ShutterSpeed、MeteringMode）

4. **相机模式限制**
   - AUTO 模式下，`EdsGetPropertyDesc` 调用失败（错误码 0x00000061）
   - 导致参数验证失败，无法设置参数

---

## 🏗️ 技术架构

### 系统分层

```
┌─────────────────────────────────────────┐
│         Kiosk / Settings UI              │
│  (统一使用 /local/camera/presets/*)     │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         MVP (Spring Boot)               │
│  - CameraConfigController                │
│  - CameraParamsConverter                 │
│  - CameraConfigStore                     │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│      CameraAgent (C# HTTP API)          │
│  - PropertyController                    │
│  - CameraHostRuntime                     │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│      CameraControl (C# Core)            │
│  - CameraService                         │
│  - CanonPropMap                          │
│  - CameraPropertyWhitelist               │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         EDSDK (Canon SDK)               │
│  - 底层相机控制                          │
└─────────────────────────────────────────┘
```

### 数据流

1. **Kiosk/Settings** → 发送可读字符串值（"DAYLIGHT", "F2.8"）
2. **MVP CameraParamsConverter** → 转换为 EDSDK 编码值（Integer）
3. **CameraAgent** → HTTP API 调用
4. **CameraControl** → EDSDK 底层设置

---

## 🔧 技术实现

### 1. 参数转换层（CameraParamsConverter）

**技术点：**
- **设计模式：** Strategy Pattern（策略模式）
- **数据结构：** HashMap 映射表
- **转换逻辑：** 字符串/数值 → EDSDK 编码值

**实现细节：**

```java
@Component
public class CameraParamsConverter {
  // 7 个参数的映射表
  private static final Map<String, Integer> ISO_MAP = ...;
  private static final Map<String, Integer> WB_MAP = ...;
  private static final Map<Double, Integer> EV_MAP = ...;
  private static final Map<String, Integer> PICTURE_STYLE_MAP = ...;
  private static final Map<String, Integer> APERTURE_MAP = ...;
  private static final Map<String, Integer> SHUTTER_SPEED_MAP = ...;
  private static final Map<String, Integer> METERING_MODE_MAP = ...;
  
  public Map<String, Integer> convertToEdsdkProps(CameraParams params) {
    // 转换逻辑
  }
}
```

**关键映射表：**

| 参数 | 输入格式 | 输出格式 | 示例 |
|------|---------|---------|------|
| ISO | Integer | Integer | 1600 → 104 |
| WhiteBalance | String | Integer | "TUNGSTEN" → 4 |
| ExposureComp | Double (EV) | Integer | 0.3 → 3 |
| PictureStyle | String | Integer | "PORTRAIT" → 130 |
| Aperture | String | Integer | "F2.8" → 24 |
| ShutterSpeed | String | Integer | "1/60" → 48 |
| MeteringMode | String | Integer | "CENTER_WEIGHTED" → 5 |

---

### 2. Preset 管理系统

**技术点：**
- **数据模型：** JSON 配置文件（camera.json）
- **持久化：** Jackson ObjectMapper
- **兼容性：** 自动合并新 preset 到已存在的配置

**数据模型：**

```java
public class CameraConfig {
  private String activePresetId;
  private CameraParams params;
  private List<CameraPreset> presets;
  
  public static class CameraPreset {
    private String id;
    private String name;
    private String displayName;      // 新增：中文显示名
    private String category;          // 新增：BUSINESS/ENV
    private String legacyProfileId;   // 新增：映射到旧系统
    private List<String> tags;
    private CameraParams params;
  }
}
```

**Preset 类型：**

1. **环境预设（ENV）** - 4 个
   - `preset_day_outdoor` - 白天·室外
   - `preset_night_indoor` - 夜晚·室内
   - `preset_day_indoor` - 白天·室内
   - `preset_night_outdoor` - 夜晚·室外

2. **业务场景预设（BUSINESS）** - 4 个
   - `preset_business_medical` → `medical_standard`
   - `preset_business_idphoto` → `id_photo`
   - `preset_business_expo_pretty` → `event_marketing`
   - `preset_business_family_archive` → `elder_care`

---

### 3. Legacy Profile 集成

**技术点：**
- **设计模式：** Adapter Pattern（适配器模式）
- **向后兼容：** 保留旧接口，新接口内部调用旧服务

**实现逻辑：**

```java
@PostMapping("/presets/apply")
public ResponseEntity<?> applyPreset(@RequestBody Map<String, String> requestBody) {
  CameraPreset preset = findPreset(presetId);
  
  if (preset.getLegacyProfileId() != null) {
    // 使用旧系统
    CameraProfileService.ApplyProfileResult result = 
        profileService.applyProfile(preset.getLegacyProfileId(), false);
    // 更新 camera.json.activePresetId
  } else {
    // 使用新系统
    Map<String, Integer> edsdkProps = paramsConverter.convertToEdsdkProps(preset.getParams());
    // 应用参数
  }
}
```

**优势：**
- ✅ 不破坏旧系统
- ✅ Kiosk/Settings 只需调用新接口
- ✅ 两套系统并存，平滑迁移

---

### 4. CameraControl 扩展

**技术点：**
- **C# 语言特性：** static class, const string
- **EDSDK 集成：** PropID 映射

**修改文件：**

1. **CameraPropertyKey.cs**
   ```csharp
   public static class CameraPropertyKey {
     public const string APERTURE = "APERTURE";
     public const string SHUTTER_SPEED = "SHUTTER_SPEED";
     public const string METERING_MODE = "METERING_MODE";
   }
   ```

2. **CameraPropertyWhitelist.cs**
   ```csharp
   private static readonly HashSet<string> AllowedKeys = new HashSet<string> {
     // ... 原有 4 个
     CameraPropertyKey.APERTURE,
     CameraPropertyKey.SHUTTER_SPEED,
     CameraPropertyKey.METERING_MODE
   };
   ```

3. **CanonPropMap.cs**
   ```csharp
   private static readonly Dictionary<string, uint> _map = new Dictionary<string, uint> {
     // ... 原有映射
     { CameraPropertyKey.APERTURE, EDSDK.PropID_Av },
     { CameraPropertyKey.SHUTTER_SPEED, EDSDK.PropID_Tv },
     { CameraPropertyKey.METERING_MODE, EDSDK.PropID_MeteringMode }
   };
   ```

---

### 5. 参数验证优化

**问题：** AUTO 模式下 `EdsGetPropertyDesc` 失败（错误码 0x00000061）

**解决方案：** 优雅降级 - 验证失败时跳过验证，继续尝试设置

**实现：**

```csharp
if (validate) {
  try {
    var candidates = await GetPropDescAsync(propId);
    // 验证逻辑
  } catch (Exception ex) {
    // 如果获取属性描述失败（如 AUTO 模式），记录警告但继续尝试设置
    Logger.Warn($"Failed to get property description for {key}, skipping validation: {ex.Message}");
    // 继续执行设置逻辑
  }
}
```

**优势：**
- ✅ 在 AUTO 模式下也能尝试设置参数
- ✅ 如果相机支持，参数仍能设置成功
- ✅ 不影响正常模式下的验证

---

## 📝 开发步骤复盘

### Step 1: MVP 侧 - ApplyParams + ApplyPreset API

**任务：**
- 实现 `POST /local/camera/apply-params`（部分更新）
- 实现 `POST /local/camera/presets/apply`（应用预设）

**开发过程：**
1. ✅ 创建 `CameraParamsConverter`（初始只支持 4 个参数）
2. ✅ 在 `CameraConfigController` 中添加 `applyParams()` 方法
3. ✅ 在 `CameraConfigController` 中添加 `applyPreset()` 方法
4. ✅ 实现参数持久化（写回 camera.json）
5. ✅ 错误处理（返回 failedField 和 reason）

**技术难点：**
- 参数转换逻辑（字符串 → EDSDK 编码值）
- 部分更新逻辑（只更新提供的字段）

---

### Step 2: CameraControl 扩展 - 支持 7 参数

**任务：**
- 扩展 CameraControl 支持 APERTURE、SHUTTER_SPEED、METERING_MODE

**开发过程：**
1. ✅ 在 `CameraPropertyKey` 中添加 3 个新常量
2. ✅ 在 `CameraPropertyWhitelist` 中添加白名单
3. ✅ 在 `CanonPropMap` 中添加 EDSDK PropID 映射
4. ✅ 增强 `PropertyController` 错误响应（添加 failedField）
5. ✅ 修复 `CameraService.SetPropertyAsync` 验证逻辑（AUTO 模式兼容）

**技术难点：**
- EDSDK PropID 映射（需要查阅 EDSDK 文档）
- 参数验证失败时的优雅降级

**遇到的问题：**
- ❌ 错误码 0x00000061（设备忙/属性不可访问）
- ✅ 解决：跳过验证，继续尝试设置

---

### Step 3: Business Presets 合并

**任务：**
- 将旧的 4 个业务场景合并到新的 preset 系统

**开发过程：**
1. ✅ 扩展 `CameraPreset` 类（添加 legacyProfileId、displayName、category）
2. ✅ 在 `CameraConfig.initDefaultPresets()` 中添加 4 个 business presets
3. ✅ 修改 `applyPreset()` 方法，支持 legacyProfileId 分支
4. ✅ 实现 `GET /local/camera/presets` 接口
5. ✅ 修复 `CameraConfigStore.load()` 自动合并新 preset

**技术难点：**
- 向后兼容（不破坏旧系统）
- 自动合并逻辑（已存在的 camera.json 需要合并新 preset）

**设计决策：**
- ✅ 使用 Adapter Pattern，新接口内部调用旧服务
- ✅ 不删除旧接口，两套系统并存
- ✅ Kiosk/Settings 统一使用新接口

---

### Step 4: 扩展 CameraParamsConverter

**任务：**
- 支持 APERTURE、SHUTTER_SPEED、METERING_MODE 转换

**开发过程：**
1. ✅ 添加 `APERTURE_MAP` 映射表（F 值 → 编码值）
2. ✅ 添加 `SHUTTER_SPEED_MAP` 映射表（1/XX → 编码值）
3. ✅ 添加 `METERING_MODE_MAP` 映射表（字符串 → 编码值）
4. ✅ 修复 `EV_MAP` 映射（根据实际候选值调整）
5. ✅ 在 `convertToEdsdkProps()` 中添加转换逻辑

**技术难点：**
- 根据实际相机值调整映射表（不同相机型号可能不同）
- 快门速度映射（编码值越大，快门越快）

**数据来源：**
- 通过 `/property/desc` 接口获取相机实际候选值
- 根据实际值调整映射表

---

### Step 5: 添加修改 Preset 参数 API

**任务：**
- 实现 `PUT /local/camera/presets/{presetId}/params`

**开发过程：**
1. ✅ 在 `CameraConfigController` 中添加 `updatePresetParams()` 方法
2. ✅ 实现部分更新逻辑（只更新提供的字段）
3. ✅ 限制：不允许修改 legacy preset 的参数
4. ✅ 持久化到 camera.json

**技术难点：**
- 部分更新逻辑（保持未提供的字段不变）

---

## 🛠️ 核心技术栈

### 后端（MVP - Spring Boot）

| 技术 | 用途 | 版本 |
|------|------|------|
| Spring Boot | Web 框架 | 3.x |
| Jackson | JSON 序列化/反序列化 | 内置 |
| SLF4J + Logback | 日志 | 内置 |
| `@ConfigurationProperties` | 配置绑定 | Spring Boot |
| `ApplicationRunner` | 启动时执行 | Spring Boot |
| `RestTemplate` | HTTP 客户端 | Spring Boot |

### 相机控制（CameraControl - C#）

| 技术 | 用途 | 版本 |
|------|------|------|
| C# | 编程语言 | .NET Framework |
| EDSDK | Canon 相机 SDK | 最新 |
| ASP.NET Web API | HTTP API 服务 | 4.x |
| Task/async-await | 异步编程 | C# |

### 数据存储

| 格式 | 用途 | 位置 |
|------|------|------|
| JSON | 配置文件 | `camera.json`, `device.json` |
| Jackson | JSON 处理 | Java 侧 |

---

## 📊 API 接口清单

### MVP 接口（localhost 限制）

| 方法 | 路径 | 功能 | 状态 |
|------|------|------|------|
| GET | `/local/camera/config` | 获取相机配置 | ✅ |
| PUT | `/local/camera/config` | 保存相机配置 | ✅ |
| GET | `/local/camera/status` | 获取相机状态 | ✅ |
| POST | `/local/camera/test-shot` | 测试拍照 | ✅ |
| POST | `/local/camera/apply-params` | 应用参数（部分更新） | ✅ |
| POST | `/local/camera/presets/apply` | 应用预设 | ✅ |
| GET | `/local/camera/presets` | 获取预设列表 | ✅ |
| PUT | `/local/camera/presets/{presetId}/params` | 更新预设参数 | ✅ |

### CameraAgent 接口

| 方法 | 路径 | 功能 | 状态 |
|------|------|------|------|
| GET | `/property/get?key=ISO` | 获取属性值 | ✅ |
| POST | `/property/set` | 设置属性值 | ✅ |
| GET | `/property/desc?key=ISO` | 获取属性描述（候选值） | ✅ |
| GET | `/status` | 获取相机状态 | ✅ |
| POST | `/capture` | 拍照 | ✅ |

---

## 🔍 关键设计决策

### 1. 参数表示方式

**决策：** 对外（Kiosk/Settings/MVP API）使用可读字符串，对内（CameraAgent）使用 EDSDK 编码值

**理由：**
- ✅ 用户体验好（"DAYLIGHT" 比 1 更直观）
- ✅ 易于调试和维护
- ✅ 转换层集中管理，便于调整

### 2. Legacy Profile 集成方式

**决策：** 使用 Adapter Pattern，新 preset 带 `legacyProfileId`，内部调用旧服务

**理由：**
- ✅ 不破坏旧系统
- ✅ 不需要反向映射（EDSDK 编码值 → 字符串）
- ✅ 平滑迁移，两套系统并存

### 3. 参数验证策略

**决策：** 验证失败时优雅降级，跳过验证继续尝试设置

**理由：**
- ✅ 兼容 AUTO 模式
- ✅ 如果相机支持，参数仍能设置成功
- ✅ 不影响正常模式下的验证

### 4. Preset 自动合并

**决策：** `CameraConfigStore.load()` 时自动检测并合并新 preset

**理由：**
- ✅ 向后兼容（已存在的 camera.json 自动升级）
- ✅ 无需手动迁移配置
- ✅ 用户体验好

---

## 🐛 遇到的问题与解决方案

### 问题 1: 错误码 0x00000061（设备忙）

**现象：**
- AUTO 模式下，`EdsGetPropertyDesc` 调用失败
- 导致参数验证失败，无法设置参数

**原因：**
- AUTO 模式下，相机不允许获取某些参数的候选值列表

**解决方案：**
- 在 `CameraService.SetPropertyAsync` 中，验证失败时跳过验证，继续尝试设置
- 添加 `Logger.Warn` 记录警告，但不抛出异常

**代码：**
```csharp
try {
  var candidates = await GetPropDescAsync(propId);
  // 验证逻辑
} catch (Exception ex) {
  Logger.Warn($"Failed to get property description, skipping validation: {ex.Message}");
  // 继续执行设置逻辑
}
```

---

### 问题 2: Preset 未出现在列表中

**现象：**
- 已存在的 `camera.json` 只包含旧的 4 个环境预设
- 新的 4 个业务场景预设未出现

**原因：**
- `CameraConfigStore.load()` 只检查 presets 是否为空，不检查是否缺少新 preset

**解决方案：**
- 在 `load()` 方法中添加自动合并逻辑
- 检测是否缺少 business presets，自动从默认配置中合并

**代码：**
```java
boolean hasBusinessPresets = config.getPresets().stream()
    .anyMatch(p -> p.getId() != null && p.getId().startsWith("preset_business_"));

if (!hasBusinessPresets) {
  // 从默认配置中获取并合并
  for (CameraPreset defaultPreset : defaultConfig.getPresets()) {
    if (defaultPreset.getId().startsWith("preset_business_")) {
      config.getPresets().add(defaultPreset);
    }
  }
}
```

---

### 问题 3: Bean 冲突

**现象：**
- `ConflictingBeanDefinitionException`: 两个 `DeviceIdentityStore` 类冲突

**原因：**
- 存在两个同名类：`.device.DeviceIdentityStore` 和 `.platform.DeviceIdentityStore`

**解决方案：**
- 使用 `@Qualifier` 明确指定 bean 名称
- 在 `.device` 包中使用 `@Component("deviceDeviceIdentityStore")`
- 在注入时使用 `@Qualifier("deviceDeviceIdentityStore")`

---

### 问题 4: Logger.Warn 方法不存在

**现象：**
- 编译错误：`Logger` 未包含 `Warn` 的定义

**原因：**
- `Logger` 类只有 `Info` 和 `Error` 方法

**解决方案：**
- 在 `Logger.cs` 中添加 `Warn` 方法

**代码：**
```csharp
public static void Warn(string msg) => Console.WriteLine("[" + DateTime.Now.ToString("HH:mm:ss.fff") + "] [WARN] " + msg);
```

---

## 📈 开发成果

### 功能完成度

| 功能模块 | 完成度 | 说明 |
|---------|--------|------|
| 参数转换（7 参数） | ✅ 100% | ISO、WB、ExposureComp、PictureStyle、Aperture、ShutterSpeed、MeteringMode |
| ApplyParams API | ✅ 100% | 支持部分更新 |
| ApplyPreset API | ✅ 100% | 支持新 preset 和 legacy preset |
| Business Presets 合并 | ✅ 100% | 4 个业务场景已合并 |
| Preset 列表 API | ✅ 100% | GET /local/camera/presets |
| 更新 Preset 参数 API | ✅ 100% | PUT /local/camera/presets/{presetId}/params |
| CameraControl 扩展 | ✅ 100% | 支持 7 个参数 |
| 参数验证优化 | ✅ 100% | AUTO 模式兼容 |

### 代码统计

| 模块 | 新增文件 | 修改文件 | 代码行数 |
|------|---------|---------|---------|
| MVP (Java) | 2 | 3 | ~800 行 |
| CameraControl (C#) | 0 | 4 | ~100 行 |
| 测试文档 | 8 | 0 | ~2000 行 |

---

## 🎓 技术要点总结

### 1. 参数转换设计

**核心思想：** 分层转换，对外可读，对内编码

```
用户输入（可读） → CameraParamsConverter → EDSDK 编码值 → 相机
"DAYLIGHT"      → 转换逻辑              → 1                → 设置成功
```

**优势：**
- 用户体验好
- 易于调试
- 集中管理映射关系

---

### 2. Legacy 系统集成

**核心思想：** Adapter Pattern，不破坏旧系统

```
新接口 → 检测 legacyProfileId → 调用旧服务 → 更新新配置
```

**优势：**
- 向后兼容
- 平滑迁移
- 两套系统并存

---

### 3. 优雅降级

**核心思想：** 验证失败时继续尝试，不直接失败

```
验证 → 失败 → 记录警告 → 继续设置 → 让相机决定
```

**优势：**
- 兼容更多场景
- 提高成功率
- 不影响正常流程

---

## 📚 相关文档

### 开发文档
- `CAMERA_APPLY_PARAMS_TEST.md` - ApplyParams + ApplyPreset 测试文档
- `BUSINESS_PRESETS_MERGE_TEST.md` - Business Presets 合并测试文档
- `UPDATE_PRESET_PARAMS_TEST.md` - 更新 Preset 参数测试文档
- `CAMERA_7PARAMS_EXTENSION_TEST.md` - CameraControl 扩展测试文档

### 快速测试
- `CAMERA_APPLY_QUICK_TEST.md` - 快速测试指南
- `BUSINESS_PRESETS_QUICK_TEST.md` - Business Presets 快速测试
- `UPDATE_PRESET_PARAMS_QUICK_TEST.md` - 更新 Preset 参数快速测试
- `CAMERA_7PARAMS_QUICK_TEST.md` - CameraControl 扩展快速测试

### 排查文档
- `TROUBLESHOOTING_CAMERA_ERROR.md` - 相机错误排查指南
- `PRESET_APPLY_VERIFICATION.md` - Preset 应用验证分析
- `CHECK_CAMERA_PARAMS.md` - 查看相机参数指南

---

## 🚀 下一步计划

### 短期（已完成）
- ✅ Phase C3: ApplyParams + ApplyPreset
- ✅ CameraControl 扩展到 7 参数
- ✅ Business Presets 合并
- ✅ 参数转换完整支持

### 中期（待完成）
- ⏳ Kiosk Settings 页面开发
- ⏳ 参数值映射表动态加载（根据相机型号）
- ⏳ 参数值范围验证（UI 层）

### 长期（规划中）
- ⏳ 参数预设模板管理（云端同步）
- ⏳ 参数历史记录（回滚功能）
- ⏳ 参数批量导入/导出

---

## 💡 经验总结

### 成功经验

1. **分层设计**
   - 参数转换层独立，易于维护和扩展
   - 接口层和业务层分离，职责清晰

2. **向后兼容**
   - 保留旧系统，新系统适配旧系统
   - 平滑迁移，不影响现有功能

3. **优雅降级**
   - 验证失败时继续尝试，提高成功率
   - 兼容更多场景（如 AUTO 模式）

4. **自动合并**
   - 配置升级自动化，用户体验好
   - 减少手动迁移工作

### 改进建议

1. **参数映射表动态化**
   - 当前映射表写死在代码中
   - 建议：根据相机型号动态加载映射表

2. **错误处理增强**
   - 当前错误信息较简单
   - 建议：提供更详细的错误原因和建议

3. **参数值验证**
   - 当前只在相机侧验证
   - 建议：在 MVP 侧也进行预验证，提前发现错误

---

## 📝 附录

### A. 参数值对照表

详见各测试文档中的参数值对照表。

### B. API 调用示例

详见各快速测试文档。

### C. 常见问题

详见 `TROUBLESHOOTING_CAMERA_ERROR.md`。

---

**文档版本：** 1.0  
**最后更新：** 2026年1月  
**维护者：** AI Photo Booth Team
