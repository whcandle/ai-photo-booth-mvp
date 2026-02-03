# Camera Config API 快速测试指南

## 快速验收（3 步）

### 步骤 1: 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

### 步骤 2: 测试 GET（自动生成默认配置）
```bash
curl http://localhost:8080/local/camera/config
```

**预期：** 返回完整 JSON，包含 4 个默认 preset

**验证文件生成：**
```bash
# PowerShell
Test-Path camera.json
# 应该返回 True
```

### 步骤 3: 测试 PUT（修改并持久化）
```bash
# 先获取当前配置
curl http://localhost:8080/local/camera/config -o config.json

# 编辑 config.json，将 params.iso 改为 200

# PUT 保存
curl -X PUT http://localhost:8080/local/camera/config -H "Content-Type: application/json" -d @config.json

# 验证：再次 GET
curl http://localhost:8080/local/camera/config | findstr "iso"
# 应该看到 "iso": 200
```

---

## 验收标准

✅ **GET 返回 JSON**  
✅ **PUT 修改后文件持久化**（重启 MVP 后仍有效）  
✅ **非 localhost 访问返回 403**

---

## 详细测试文档

完整测试步骤请参考：`CAMERA_CONFIG_API_TEST.md`
