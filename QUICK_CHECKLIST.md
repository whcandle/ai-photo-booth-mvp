# 快速检查清单（5分钟验证）

## ✅ 必须检查的 5 个点

### 1. 编译和启动
```bash
mvn clean compile
mvn spring-boot:run
```
**预期**：应用正常启动，无异常

---

### 2. 读取配置 API
```bash
curl http://127.0.0.1:8080/local/device/config
```
**预期**：返回 JSON，包含所有字段

**检查**：
- [ ] `deviceId` 是 String（不是数字）
- [ ] `tokenExpiresAt` 是 ISO8601 格式（如：`2024-12-31T23:59:59Z`）

---

### 3. 保存配置 API（关键：不覆盖只读字段）
```bash
# 先读取，记住 deviceId 和 deviceToken
curl http://127.0.0.1:8080/local/device/config > before.json

# 保存新配置
curl -X PUT http://127.0.0.1:8080/local/device/config \
  -H "Content-Type: application/json" \
  -d '{"platformBaseUrl":"http://test","deviceCode":"test","secret":"test"}'

# 再次读取
curl http://127.0.0.1:8080/local/device/config > after.json

# 对比：deviceId 和 deviceToken 应该保持不变
```
**预期**：`deviceId` 和 `deviceToken` 保持不变

**检查**：
- [ ] 可写字段（platformBaseUrl/deviceCode/secret）已更新
- [ ] 只读字段（deviceId/deviceToken/tokenExpiresAt）保持不变

---

### 4. Bootstrap Handshake（启动时自动）
**步骤**：
1. 删除或清空 `deviceId` 和 `deviceToken`（保留 platformBaseUrl/deviceCode/secret）
2. 重启应用
3. 检查日志和 device.json

**预期**：
- ✅ 应用启动时自动执行 handshake
- ✅ device.json 包含新的 deviceId 和 deviceToken
- ✅ **platformBaseUrl/deviceCode/secret 保持不变**

**检查**：
- [ ] handshake 成功执行
- [ ] 可写字段（platformBaseUrl/deviceCode/secret）保持不变

---

### 5. 向后兼容性（读取旧格式）
**步骤**：
1. 创建旧格式 device.json（deviceId 是数字）：
```json
{
  "platformBaseUrl": "http://127.0.0.1:8089",
  "deviceCode": "test",
  "secret": "test",
  "deviceId": 123,
  "deviceToken": "old_token",
  "tokenExpiresAt": "2024-12-31T23:59:59Z"
}
```

2. 启动应用

**预期**：
- ✅ 应用能正常读取旧格式
- ✅ deviceId 自动转换为 String

**检查**：
- [ ] 应用正常启动
- [ ] 配置读取正常

---

## 🚨 如果发现问题

### 问题 1：deviceId 类型错误
**症状**：API 返回 deviceId 是数字而不是字符串
**解决**：检查 DeviceConfigStore.load() 的兼容性处理

### 问题 2：数据被覆盖
**症状**：保存配置后，deviceId/token 丢失
**解决**：检查 DeviceConfigController.saveDeviceConfig() 是否正确保留只读字段

### 问题 3：Handshake 覆盖 UI 配置
**症状**：执行 handshake 后，platformBaseUrl/deviceCode/secret 被清空
**解决**：检查 DeviceBootstrapRunner 是否正确保留可写字段

---

## 📝 使用 PowerShell 脚本快速验证

```powershell
.\quick_test_device_config.ps1
```

脚本会自动检查：
- ✅ 应用是否运行
- ✅ 配置读取
- ✅ 配置保存（保留只读字段）
- ✅ 代理接口
- ✅ device.json 文件格式

---

## ✅ 验收标准

所有检查点通过 = 重构成功 ✅
