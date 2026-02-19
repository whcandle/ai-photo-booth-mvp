# 快速测试模板包安装

## 最简单的测试方法

### 方法 1：使用测试脚本（推荐）

```powershell
.\test_template_install.ps1 `
  -templateId 2 `
  -version "1.0.0" `
  -downloadUrl "https://example.com/template.zip" `
  -checksum "sha256:abc123..."
```

### 方法 2：使用 curl

```bash
curl -X POST "http://127.0.0.1:8080/local/device/templates/install" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": 2,
    "version": "1.0.0",
    "downloadUrl": "https://example.com/template.zip",
    "checksum": "sha256:abc123..."
  }'
```

### 方法 3：PowerShell 一行命令

```powershell
$body = @{templateId=2; version="1.0.0"; downloadUrl="https://..."; checksum="sha256:..."} | ConvertTo-Json
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/templates/install" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing
```

---

## 验证安装成功

### 1. 检查 manifest.json

```powershell
# 检查文件是否存在
Test-Path "data/templates/2/1.0.0/manifest.json"

# 查看内容
Get-Content "data/templates/2/1.0.0/manifest.json" | ConvertFrom-Json | ConvertTo-Json
```

### 2. 检查 index.json

```powershell
# 查看索引
Get-Content "data/index.json" | ConvertFrom-Json | ConvertTo-Json

# 或使用 API
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/templates/installed" -UseBasicParsing
```

### 3. 检查目录结构

```powershell
# 列出模板目录
Get-ChildItem "data/templates" -Recurse

# 检查是否有 tmp 残留
Get-ChildItem "data/tmp" -Recurse
```

---

## 测试错误场景

### 1. Checksum 不匹配

```powershell
.\test_template_install.ps1 `
  -templateId 2 `
  -version "1.0.0" `
  -downloadUrl "https://example.com/template.zip" `
  -checksum "sha256:wrong_checksum"
```

**预期**：
- 返回 `success=false`
- ZIP 文件被删除
- `data/templates/2/1.0.0/` 目录不存在

### 2. 下载失败（无效 URL）

```powershell
.\test_template_install.ps1 `
  -templateId 2 `
  -version "1.0.0" `
  -downloadUrl "https://invalid-url.example.com/template.zip" `
  -checksum "sha256:abc123..."
```

**预期**：返回错误，不会创建 templates 目录

---

## 完整测试流程

```powershell
# 1. 安装模板
.\test_template_install.ps1 -templateId 2 -version "1.0.0" -downloadUrl "..." -checksum "..."

# 2. 验证安装
Test-Path "data/templates/2/1.0.0/manifest.json"

# 3. 查看索引
Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/templates/installed" -UseBasicParsing

# 4. 测试重复安装（应该替换）
.\test_template_install.ps1 -templateId 2 -version "1.0.0" -downloadUrl "..." -checksum "..."
```

---

## 预期结果

### 成功安装后

- ✅ `data/templates/<templateId>/<version>/manifest.json` 存在
- ✅ `data/index.json` 包含新条目
- ✅ `data/tmp/downloads/` 中的 ZIP 文件已清理
- ✅ `data/tmp/staging/` 中的临时目录已清理

### Checksum 不匹配

- ✅ 返回 `success=false`
- ✅ ZIP 文件被删除
- ✅ templates 目录未创建
- ✅ index.json 未更新
