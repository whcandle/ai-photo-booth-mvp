# Camera Status + Test Shot API 快速测试指南

## 快速验收（3 步）

### 步骤 1: 启动 MVP
```bash
cd d:\workspace\ai-photo-booth-mvp
mvn spring-boot:run
```

### 步骤 2: 测试状态查询
```bash
curl http://localhost:8080/local/camera/status
```

**预期：** 返回 JSON，包含 `connected: true/false`

### 步骤 3: 测试拍照（如果相机已连接）
```bash
curl -X POST http://localhost:8080/local/camera/test-shot
```

**预期：** 返回 JSON，包含 `data.path`（文件路径）

**验证文件：**
```bash
# 检查文件是否存在
Test-Path test\test_*.jpg

# 查看最新文件
Get-ChildItem test -Filter "test_*.jpg" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
```

---

## 验收标准

✅ **status 能看到 connected=true/false**  
✅ **test-shot 落盘生成 jpg**（保存在 `./test/` 目录）

---

## 详细测试文档

完整测试步骤请参考：`CAMERA_STATUS_TEST_SHOT_TEST.md`
