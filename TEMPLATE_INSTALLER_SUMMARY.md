# Template Package Installer - 实现总结

## 新增/修改的文件清单

### 修改的文件

1. **`src/main/java/com/mg/booth/config/BoothProps.java`**
   - 新增 `dataDir` 配置项（默认 `./data`）
   - 新增 getter/setter

2. **`src/main/resources/application.yml`**
   - 新增 `booth.dataDir: "./data"` 配置

### 新增的文件

1. **`src/main/java/com/mg/booth/device/LocalTemplateIndexStore.java`**
   - 模板索引存储工具类
   - 原子读写 `index.json`

2. **`src/main/java/com/mg/booth/device/TemplatePackageInstaller.java`**
   - 模板包安装服务
   - 下载、校验、解压、原子安装、索引更新

3. **`src/main/java/com/mg/booth/api/TemplateController.java`**
   - 模板安装控制器
   - `POST /local/device/templates/install`
   - `GET /local/device/templates/installed`

4. **`test_template_install.ps1`**
   - PowerShell 测试脚本

5. **`TEMPLATE_INSTALLER_IMPLEMENTATION.md`**
   - 实现文档

6. **`QUICK_TEST_TEMPLATE_INSTALL.md`**
   - 快速测试指南

---

## curl 验收命令

### 1. 安装模板包

```bash
curl -X POST "http://127.0.0.1:8080/local/device/templates/install" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": 2,
    "version": "1.0.0",
    "downloadUrl": "https://example.com/template.zip",
    "checksum": "sha256:abc123def456..."
  }'
```

**预期响应**：
```json
{
  "success": true,
  "data": {
    "installedPath": "templates/2/1.0.0",
    "indexUpdated": true
  },
  "message": null
}
```

### 2. 查看已安装模板

```bash
curl -X GET "http://127.0.0.1:8080/local/device/templates/installed"
```

**预期响应**：
```json
{
  "success": true,
  "data": {
    "schemaVersion": 1,
    "updatedAt": "2026-02-06T12:00:00.000Z",
    "items": [
      {
        "templateId": 2,
        "version": "1.0.0",
        "path": "templates/2/1.0.0",
        "installedAt": "2026-02-06T12:00:00.000Z",
        "checksum": "abc123def456...",
        "downloadUrl": "https://example.com/template.zip"
      }
    ]
  },
  "message": null
}
```

### 3. 测试 checksum 校验失败

```bash
curl -X POST "http://127.0.0.1:8080/local/device/templates/install" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": 2,
    "version": "1.0.0",
    "downloadUrl": "https://example.com/template.zip",
    "checksum": "sha256:wrong_checksum"
  }'
```

**预期响应**：
```json
{
  "success": false,
  "data": null,
  "message": "Installation failed: Checksum mismatch: expected=wrong_checksum, actual=abc123..."
}
```

---

## 最简要测试方法

### PowerShell 一行命令

```powershell
# 安装模板（替换为实际的 URL 和 checksum）
$body = @{
    templateId = 2
    version = "1.0.0"
    downloadUrl = "https://example.com/template.zip"
    checksum = "sha256:abc123..."
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/templates/install" `
  -Method POST -Body $body -ContentType "application/json" -UseBasicParsing
```

### 验证安装

```powershell
# 1. 检查 manifest.json
Test-Path "data/templates/2/1.0.0/manifest.json"

# 2. 查看索引
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/templates/installed" -UseBasicParsing

# 3. 查看目录结构
Get-ChildItem "data/templates" -Recurse
```

---

## 验收检查清单

### ✅ 基本功能

- [ ] 安装成功：返回 `success=true`，`installedPath` 存在
- [ ] 目录创建：`data/templates/<templateId>/<version>/` 存在
- [ ] manifest.json：存在于安装目录
- [ ] index.json：已更新，包含新条目

### ✅ 原子性

- [ ] 安装成功后：`data/templates/` 目录结构完整
- [ ] 安装失败后：`data/templates/` 目录未创建（或已清理）
- [ ] tmp 文件：成功安装后 ZIP 文件已清理

### ✅ 校验

- [ ] Checksum 正确：安装成功
- [ ] Checksum 错误：返回错误，ZIP 删除，目录未创建

### ✅ 并发

- [ ] 同时安装同一 templateId+version：串行执行，不冲突

---

## 目录结构验证

安装成功后，应该看到：

```
data/
  templates/
    2/
      1.0.0/
        manifest.json
        assets/... (如果有)
  index.json
  tmp/
    downloads/ (应该为空或只有临时文件)
    staging/ (应该为空)
```

---

## 注意事项

1. **需要真实的 downloadUrl 和 checksum**：测试时需要提供可访问的 ZIP 文件 URL 和正确的 SHA256
2. **manifest.json 格式**：ZIP 包内必须包含 `manifest.json`，且包含 `templateId` 和 `version` 字段
3. **Windows 兼容**：ATOMIC_MOVE 可能失败，已实现 fallback
4. **临时文件**：失败时可能残留 tmp 文件，可手动清理
