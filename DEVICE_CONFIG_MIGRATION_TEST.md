# Device.json 单一真源重构 - 测试检查清单

## 📋 测试目标

验证重构后：
1. ✅ 向后兼容：旧的 device.json 格式能正常读取
2. ✅ 功能正常：所有现有功能不受影响
3. ✅ 原子写：device.json 写入不会互相覆盖
4. ✅ 数据一致性：UI 配置和 bootstrap handshake 不会互相覆盖

---

## 🔍 检查清单

### 阶段 1：编译和启动检查

#### ✅ 1.1 编译检查
```bash
cd D:\workspace\ai-photo-booth-mvp
mvn clean compile
```
**预期结果**：编译成功，无错误

#### ✅ 1.2 启动检查（无 device.json）
```bash
# 删除 device.json（如果存在）
rm device.json

# 启动应用
mvn spring-boot:run
```
**预期结果**：
- ✅ 应用正常启动，不报错
- ✅ 日志中出现：`[device-config] device.json not found at ..., creating default config`
- ✅ 自动创建了 device.json 文件（默认配置）

**检查点**：
- [ ] 应用启动成功
- [ ] 没有 `UnsupportedOperationException` 异常
- [ ] device.json 文件被创建

---

### 阶段 2：向后兼容性检查

#### ✅ 2.1 读取旧格式 device.json（使用 Instant）
创建测试文件 `device.json`：
```json
{
  "platformBaseUrl": "http://127.0.0.1:8089",
  "deviceCode": "test_device",
  "secret": "test_secret",
  "deviceId": "123",
  "deviceToken": "old_token_123",
  "tokenExpiresAt": "2024-12-31T23:59:59Z"
}
```

**预期结果**：
- ✅ DeviceConfigStore.load() 能正常读取
- ✅ tokenExpiresAt 作为 String 存储（ISO8601 格式）
- ✅ 应用正常启动

**检查点**：
- [ ] 能读取旧的 device.json
- [ ] tokenExpiresAt 格式正确（ISO8601 String）

#### ✅ 2.2 读取旧格式 device.json（使用 Long deviceId）
```json
{
  "platformBaseUrl": "http://127.0.0.1:8089",
  "deviceCode": "test_device",
  "secret": "test_secret",
  "deviceId": 123,
  "deviceToken": "old_token_123",
  "tokenExpiresAt": "2024-12-31T23:59:59Z"
}
```

**预期结果**：
- ✅ Jackson 自动将 Long 转换为 String（或需要手动处理）
- ✅ 应用正常启动

**检查点**：
- [ ] Long 类型的 deviceId 能正常读取

---

### 阶段 3：核心功能检查

#### ✅ 3.1 UI 配置读取（GET /local/device/config）
```bash
curl http://127.0.0.1:8080/local/device/config
```

**预期结果**：
```json
{
  "success": true,
  "data": {
    "platformBaseUrl": "http://127.0.0.1:8089",
    "deviceCode": "test_device",
    "secret": "test_secret",
    "deviceId": "123",
    "deviceToken": "old_token_123",
    "tokenExpiresAt": "2024-12-31T23:59:59Z"
  },
  "message": null
}
```

**检查点**：
- [ ] 返回正确的配置数据
- [ ] deviceId 是 String 类型
- [ ] tokenExpiresAt 是 ISO8601 String

#### ✅ 3.2 UI 配置保存（PUT /local/device/config）
```bash
curl -X PUT http://127.0.0.1:8080/local/device/config \
  -H "Content-Type: application/json" \
  -d '{
    "platformBaseUrl": "http://127.0.0.1:8089",
    "deviceCode": "new_device_code",
    "secret": "new_secret"
  }'
```

**预期结果**：
- ✅ 返回 success: true
- ✅ device.json 文件被更新（原子写）
- ✅ **保留** deviceId、deviceToken、tokenExpiresAt（只更新可写字段）

**检查点**：
- [ ] 配置保存成功
- [ ] device.json 文件内容正确
- [ ] 只读字段（deviceId/token）未被覆盖

#### ✅ 3.3 Bootstrap Handshake（启动时自动执行）
准备 device.json（无 deviceId/token）：
```json
{
  "platformBaseUrl": "http://127.0.0.1:8089",
  "deviceCode": "valid_device_code",
  "secret": "valid_secret"
}
```

**预期结果**：
- ✅ 应用启动时自动执行 handshake
- ✅ device.json 被更新，包含 deviceId 和 deviceToken
- ✅ tokenExpiresAt 是 ISO8601 格式
- ✅ **保留** platformBaseUrl、deviceCode、secret（只更新只读字段）

**检查点**：
- [ ] handshake 成功执行
- [ ] device.json 包含 deviceId 和 deviceToken
- [ ] 可写字段（platformBaseUrl/deviceCode/secret）未被覆盖

#### ✅ 3.4 代理接口（GET /api/v1/device/activities）
前提：device.json 包含有效的 deviceId 和 deviceToken

```bash
curl http://127.0.0.1:8080/api/v1/device/activities
```

**预期结果**：
- ✅ 返回活动列表或错误信息
- ✅ 能正确读取 deviceId（String → Long 转换）

**检查点**：
- [ ] 能正确读取 deviceId
- [ ] API 调用正常

---

### 阶段 4：数据一致性检查（关键）

#### ✅ 4.1 UI 配置 → Bootstrap Handshake（不覆盖）
**步骤**：
1. 通过 UI 设置 platformBaseUrl、deviceCode、secret
2. 删除 deviceId 和 deviceToken（或设为 null）
3. 重启应用，触发 bootstrap handshake

**预期结果**：
- ✅ handshake 后，deviceId 和 deviceToken 被写入
- ✅ **UI 设置的 platformBaseUrl、deviceCode、secret 保持不变**

**检查点**：
- [ ] UI 配置未被覆盖
- [ ] handshake 数据正确写入

#### ✅ 4.2 Bootstrap Handshake → UI 配置（不覆盖）
**步骤**：
1. 启动应用，执行 handshake（写入 deviceId/token）
2. 通过 UI 修改 platformBaseUrl、deviceCode、secret

**预期结果**：
- ✅ UI 配置保存成功
- ✅ **handshake 写入的 deviceId、deviceToken、tokenExpiresAt 保持不变**

**检查点**：
- [ ] handshake 数据未被覆盖
- [ ] UI 配置正确保存

---

### 阶段 5：错误处理检查

#### ✅ 5.1 禁用方法调用检查
如果代码中还有地方调用 `DeviceIdentityStore.save()`，应该抛出异常。

**检查点**：
- [ ] 所有 save() 调用都通过 DeviceConfigStore
- [ ] 没有调用已禁用的 DeviceIdentityStore.save()

#### ✅ 5.2 Token 验证检查
```java
// DeviceConfig.isTokenValid() 应该正常工作
```

**检查点**：
- [ ] token 过期检查正常
- [ ] 30 秒缓冲时间正确

---

## 🚨 常见问题排查

### 问题 1：device.json 格式不兼容
**症状**：读取 device.json 失败
**解决**：
- 检查 JSON 格式是否正确
- 检查字段类型（deviceId 应该是 String，不是 Long）

### 问题 2：数据被覆盖
**症状**：UI 配置或 handshake 数据丢失
**解决**：
- 检查 DeviceConfigController 是否正确保留只读字段
- 检查 DeviceBootstrapRunner 是否正确保留可写字段

### 问题 3：类型转换错误
**症状**：deviceId 类型不匹配
**解决**：
- 使用 `config.setDeviceIdFromLong()` 和 `config.getDeviceIdAsLong()`
- 检查 JSON 序列化/反序列化

---

## 📝 快速验证脚本

创建 `test_device_config.sh`：
```bash
#!/bin/bash

echo "=== 测试 1: 读取配置 ==="
curl -s http://127.0.0.1:8080/local/device/config | jq .

echo -e "\n=== 测试 2: 保存配置 ==="
curl -s -X PUT http://127.0.0.1:8080/local/device/config \
  -H "Content-Type: application/json" \
  -d '{"platformBaseUrl":"http://test","deviceCode":"test","secret":"test"}' | jq .

echo -e "\n=== 测试 3: 验证配置 ==="
curl -s http://127.0.0.1:8080/local/device/config | jq .

echo -e "\n=== 测试 4: 检查 device.json ==="
cat device.json | jq .
```

---

## ✅ 验收标准

所有检查点通过后，重构成功：
- [x] 编译和启动正常
- [x] 向后兼容（能读取旧格式）
- [x] UI 配置功能正常
- [x] Bootstrap handshake 功能正常
- [x] 代理接口功能正常
- [x] 数据一致性（不互相覆盖）
- [x] 错误处理正常

---

## 🔄 回滚方案

如果发现问题，可以：
1. 恢复 `DeviceIdentityStore.save()` 方法（移除 @Deprecated 和异常）
2. 恢复 `DeviceBootstrapRunner` 使用 `DeviceIdentityStore`
3. 恢复 `DeviceProxyController` 使用 `DeviceIdentityStore`

但建议先排查问题，因为单一真源是更好的架构。
