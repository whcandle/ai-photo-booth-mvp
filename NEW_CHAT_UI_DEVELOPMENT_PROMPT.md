# 新 Chat - UI 开发开场提示

## 📋 直接复制使用

```
我要开始开发 Kiosk Settings 页面，用于相机参数管理和预设选择。

## 项目背景

这是一个 AI Photo Booth 项目，包含：
- **MVP (Spring Boot)** - 后端服务，已完成 Phase C3
- **Kiosk (Vue.js)** - 前端界面，需要开发 Settings 页面
- **CameraControl (C#)** - 底层相机控制服务

## 已完成的工作（后端）

MVP 后端已完成以下功能：
- ✅ 相机参数管理 API（7个参数：ISO、WB、ExposureComp、PictureStyle、Aperture、ShutterSpeed、MeteringMode）
- ✅ 预设管理系统（4个环境预设 + 4个业务场景预设）
- ✅ 参数转换工具（可读字符串 → EDSDK 编码值）
- ✅ 应用预设/参数 API
- ✅ 测试拍照 API

**相关文档：**
- `API_CALL_EXAMPLES.md` - 完整的 API 调用示例
- `CAMERA_PARAMS_VALUE_REFERENCE.md` - 参数值完整对照表
- `PHASE_C3_DEVELOPMENT_SUMMARY.md` - 开发总结和技术架构

## 需要开发的 UI 功能

### 1. 预设选择页面
- 显示所有可用预设（环境预设 + 业务场景预设）
- 按 category 分组显示（ENV / BUSINESS）
- 显示预设的 displayName、参数预览
- 点击应用预设
- 显示当前激活的预设

### 2. 参数调整页面
- 显示当前相机参数（7个参数）
- 支持手动调整参数值
- 显示参数的可选值范围（下拉框/滑块）
- 实时预览参数值
- 应用参数按钮
- 测试拍照按钮

### 3. 预设管理页面（可选）
- 查看预设列表
- 编辑预设参数
- 创建新预设（可选）

## 技术栈

- **前端框架：** Vue.js（需要确认版本）
- **UI 组件库：** 待确认（Element UI / Ant Design Vue / 自定义）
- **HTTP 客户端：** axios（推荐）
- **状态管理：** Vuex / Pinia（可选）

## API 端点（localhost:8080）

### 核心 API
- `GET /local/camera/config` - 获取相机配置
- `GET /local/camera/presets` - 获取预设列表
- `POST /local/camera/presets/apply` - 应用预设
- `POST /local/camera/apply-params` - 应用参数
- `GET /local/camera/status` - 获取相机状态
- `POST /local/camera/test-shot` - 测试拍照
- `PUT /local/camera/presets/{presetId}/params` - 更新预设参数

**限制：** 所有接口只允许 localhost 访问

## 参数值格式

### ISO
- 类型：Integer
- 示例：100, 200, 400, 800, 1600, 3200, 6400
- 特殊值：0 = AUTO

### WhiteBalance
- 类型：String
- 可选值：AUTO, DAYLIGHT, SHADE, CLOUDY, TUNGSTEN, FLUORESCENT, FLASH, KELVIN

### ExposureCompensationEv
- 类型：Double
- 范围：-3.0 到 +3.0，步进 0.3
- 示例：-1.0, -0.3, 0.0, 0.3, 1.0

### PictureStyle
- 类型：String
- 可选值：STANDARD, PORTRAIT, LANDSCAPE, NEUTRAL, FAITHFUL, MONOCHROME

### Aperture
- 类型：String（必须以 "F" 开头）
- 示例：F2.8, F4.0, F5.6, F8.0

### ShutterSpeed
- 类型：String
- 示例：1/60, 1/125, 1/250, 1/500

### MeteringMode
- 类型：String
- 可选值：EVALUATIVE, PARTIAL, SPOT, CENTER_WEIGHTED

## 设计要求

1. **用户体验**
   - 界面清晰，参数分组合理
   - 实时反馈（应用成功/失败）
   - 错误提示明确

2. **功能完整性**
   - 支持所有 7 个参数
   - 支持预设选择和应用
   - 支持参数手动调整
   - 支持测试拍照

3. **数据展示**
   - 显示当前激活的预设
   - 显示当前参数值
   - 显示预设的参数预览

## 开发步骤建议

1. 先查看 `API_CALL_EXAMPLES.md` 了解 API 详情
2. 设计页面结构和组件
3. 实现预设选择功能
4. 实现参数调整功能
5. 集成测试拍照功能
6. 错误处理和用户反馈

## 验收标准

- ✅ 可以查看所有预设列表
- ✅ 可以应用预设（环境预设和业务场景预设）
- ✅ 可以手动调整所有 7 个参数
- ✅ 可以应用参数并看到效果
- ✅ 可以测试拍照
- ✅ 错误时显示明确的错误信息

请先查看相关文档，然后开始设计并实现 UI 页面。
```

---

## 📝 简化版（如果文档已读）

如果已经阅读过文档，可以使用这个简化版：

```
我要开发 Kiosk Settings 页面，用于相机参数管理和预设选择。

后端已完成（Phase C3），API 文档在 API_CALL_EXAMPLES.md。

需要开发：
1. 预设选择页面（显示所有预设，支持应用）
2. 参数调整页面（7个参数的手动调整）
3. 测试拍照功能

技术栈：Vue.js + axios

请先查看 API_CALL_EXAMPLES.md 了解 API，然后开始设计 UI。
```

---

## 🎯 使用建议

1. **复制完整版** - 如果这是第一次在新 chat 中讨论 UI 开发
2. **复制简化版** - 如果已经熟悉项目背景
3. **自定义修改** - 根据实际需求调整

---

**提示：** 在新 chat 中，可以直接引用文档路径，Cursor 会自动读取相关文档内容。
