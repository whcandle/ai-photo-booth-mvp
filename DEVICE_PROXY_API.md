# Device Proxy API 说明

## 新增文件

**`src/main/java/com/mg/booth/api/DeviceProxyController.java`**
- 设备代理 Controller
- 为 kiosk 提供本地代理接口，无需直接连接 platform

## API 端点

**GET** `/api/v1/device/activities`

## 功能说明

- 读取 `device.json` 获取 deviceId 和 deviceToken
- 调用 Platform API 获取活动列表
- 返回统一响应格式（success, data, message）

## 响应格式

### 成功响应

```json
{
  "success": true,
  "data": [
    {
      "activityId": 3,
      "name": "店内常驻",
      "status": "ACTIVE",
      "startAt": null,
      "endAt": null
    }
  ],
  "message": null
}
```

### 失败响应

#### 1. device.json 不存在

```json
{
  "success": false,
  "data": null,
  "message": "device.json not found"
}
```

#### 2. 设备未握手

```json
{
  "success": false,
  "data": null,
  "message": "device not handshaked yet"
}
```

#### 3. platformBaseUrl 未配置

```json
{
  "success": false,
  "data": null,
  "message": "platformBaseUrl not configured"
}
```

#### 4. 调用 Platform API 失败

```json
{
  "success": false,
  "data": null,
  "message": "Failed to fetch activities: ..."
}
```

## 测试命令

### 基本测试

```bash
curl http://127.0.0.1:8080/api/v1/device/activities
```

### 格式化输出（Windows PowerShell）

```powershell
curl http://127.0.0.1:8080/api/v1/device/activities | ConvertFrom-Json | ConvertTo-Json
```

### 格式化输出（Linux/Mac）

```bash
curl http://127.0.0.1:8080/api/v1/device/activities | jq .
```

## 验收测试

### 验收 1：成功场景（有 device.json 且已握手）

**前置条件**：
1. 运行目录下有 `device.json`，且包含 deviceId 和 deviceToken
2. Platform 服务运行正常
3. 设备已绑定活动

**测试**：
```bash
curl http://127.0.0.1:8080/api/v1/device/activities
```

**预期响应**：
```json
{
  "success": true,
  "data": [
    {
      "activityId": 3,
      "name": "店内常驻",
      "status": "ACTIVE",
      "startAt": null,
      "endAt": null
    }
  ],
  "message": null
}
```

### 验收 2：device.json 不存在

**前置条件**：
1. 运行目录下**没有** `device.json`

**测试**：
```bash
curl http://127.0.0.1:8080/api/v1/device/activities
```

**预期响应**：
```json
{
  "success": false,
  "data": null,
  "message": "device.json not found"
}
```

### 验收 3：设备未握手

**前置条件**：
1. 运行目录下有 `device.json`，但 deviceId 或 deviceToken 为 null

**测试**：
```bash
curl http://127.0.0.1:8080/api/v1/device/activities
```

**预期响应**：
```json
{
  "success": false,
  "data": null,
  "message": "device not handshaked yet"
}
```

## 注意事项

1. **无需认证**：当前接口不需要任何认证，kiosk 可以直接访问
2. **本地代理**：kiosk 只需要访问 MVP（本地服务），不需要直接连接 Platform
3. **错误处理**：所有错误都会返回统一的响应格式，不会抛出异常
4. **端口**：默认 MVP 端口是 8080，根据实际配置调整

## Kiosk 集成示例

```javascript
// kiosk 中调用
fetch('http://127.0.0.1:8080/api/v1/device/activities')
  .then(res => res.json())
  .then(data => {
    if (data.success) {
      console.log('Activities:', data.data);
    } else {
      console.error('Error:', data.message);
    }
  });
```
