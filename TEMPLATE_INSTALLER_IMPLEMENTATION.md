# Template Package Installer 实现说明

## 新增/修改的文件

### 1. `BoothProps.java`
**修改**：新增 `dataDir` 配置项（默认 `./data`）

### 2. `LocalTemplateIndexStore.java`
**新增**：模板索引存储工具类
- `readIndex(Path)`: 读取 index.json
- `writeIndex(Path, TemplateIndex)`: 原子写 index.json
- `TemplateIndex`: 索引结构（schemaVersion, updatedAt, items）
- `TemplateIndexItem`: 索引项（templateId, version, path, installedAt, checksum, downloadUrl）

### 3. `TemplatePackageInstaller.java`
**新增**：模板包安装服务
- `install(Long, String, String, String)`: 安装模板包
- 流程：下载 → 校验 → 解压 → 验证 manifest → 原子安装 → 更新索引

### 4. `TemplateController.java`
**新增**：模板安装控制器
- `POST /local/device/templates/install`: 安装模板包
- `GET /local/device/templates/installed`: 获取已安装模板索引

### 5. `application.yml`
**修改**：新增 `booth.dataDir` 配置项

---

## 目录结构

```
data/
  device.json (如果迁移到 dataDir)
  cache/
    activities_cache.json
    templates_cache_<activityId>.json
  templates/
    <templateId>/
      <version>/
        manifest.json
        assets/...
  index.json
  tmp/
    downloads/
      <uuid>.zip (临时下载文件)
    staging/
      <templateId>/
        <version>_<uuid>/ (临时解压目录)
```

---

## API 接口

### POST /local/device/templates/install

**请求体**：
```json
{
  "activityId": 1,          // 可选
  "templateId": 2,
  "version": "1.0.0",
  "downloadUrl": "https://...",
  "checksum": "sha256:abc123..."  // 或 "abc123..."
}
```

**成功响应**：
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

**错误响应**：
```json
{
  "success": false,
  "data": null,
  "message": "Installation failed: checksum mismatch"
}
```

### GET /local/device/templates/installed

**响应**：
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
        "checksum": "abc123...",
        "downloadUrl": "https://..."
      }
    ]
  },
  "message": null
}
```

---

## 安装流程

### 步骤详解

1. **下载** (`Step=download`)
   - 下载到 `tmp/downloads/<uuid>.zip.part`
   - 完成后重命名为 `.zip`

2. **校验** (`Step=verify`)
   - 计算 ZIP 文件的 SHA256
   - 与提供的 checksum 比较（支持 `sha256:` 前缀）
   - 不匹配则删除 ZIP 并返回错误

3. **解压** (`Step=unzip`)
   - 解压到 `tmp/staging/<templateId>/<version>_<uuid>/`
   - 防止 zip slip 攻击

4. **验证 manifest** (`Step=validate-manifest`)
   - 检查 `manifest.json` 必须存在
   - 验证 `templateId` 和 `version` 与入参一致

5. **原子提交** (`Step=commit`)
   - 目标目录：`data/templates/<templateId>/<version>/`
   - 如果已存在：先备份到 `.bak_<timestamp>`
   - 使用 `Files.move(..., ATOMIC_MOVE)`，失败则 fallback `REPLACE_EXISTING`

6. **更新索引** (`Step=update-index`)
   - 原子写 `data/index.json`
   - 移除旧条目（同 templateId+version）
   - 添加新条目

7. **清理** (`Step=cleanup`)
   - 删除临时 ZIP 文件
   - staging 目录在成功移动后自动清理

---

## 并发控制

- 使用 `ConcurrentHashMap<String, Lock>` 管理安装锁
- Key 格式：`"templateId:version"`
- 同一 templateId+version 的安装会串行执行，避免冲突

---

## 原子性保证

### 下载阶段
- 使用 `.part` 扩展名，完成后重命名
- 失败时删除临时文件

### 安装阶段
- staging 目录在验证通过后才移动到最终目录
- 如果移动失败，staging 目录保留（可手动清理）
- 最终目录不会出现半成品

### 索引更新
- 使用 tmp+rename 原子写
- 失败时索引保持原状

---

## 错误处理

### Checksum 不匹配
- 删除 ZIP 文件
- 返回错误，不污染 templates 目录

### Manifest 验证失败
- 清理 staging 目录
- 返回错误

### 安装中断
- 最终目录不会出现半成品（staging 未移动）
- 可能残留 tmp 文件（可手动清理）

---

## 测试方法

### 基本安装测试

```powershell
# 使用测试脚本
.\test_template_install.ps1 `
  -templateId 2 `
  -version "1.0.0" `
  -downloadUrl "https://example.com/template.zip" `
  -checksum "sha256:abc123..."
```

### 使用 curl

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

### 验证安装

```bash
# 检查 manifest.json
cat data/templates/2/1.0.0/manifest.json

# 检查 index.json
cat data/index.json | jq .
```

### 测试 checksum 校验

```bash
# 使用错误的 checksum
curl -X POST "http://127.0.0.1:8080/local/device/templates/install" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": 2,
    "version": "1.0.0",
    "downloadUrl": "https://example.com/template.zip",
    "checksum": "sha256:wrong_checksum"
  }'
```

**预期**：返回 `success=false`，ZIP 文件被删除，templates 目录未创建

---

## 最小验收清单

- [x] 配置项 `booth.dataDir` 已添加
- [x] 目录结构自动创建
- [x] 下载功能正常
- [x] SHA256 校验严格
- [x] 解压功能正常
- [x] Manifest 验证正常
- [x] 原子安装（ATOMIC_MOVE 或 fallback）
- [x] 索引更新（原子写）
- [x] 并发控制（同 templateId+version 串行）
- [x] 错误处理（checksum 不匹配、manifest 验证失败）
- [x] 中断恢复（不会污染最终目录）

---

## 注意事项

1. **Windows 兼容性**：ATOMIC_MOVE 可能失败，已实现 fallback
2. **Zip Slip 防护**：解压时检查路径，防止目录遍历攻击
3. **临时文件清理**：成功安装后清理 ZIP，失败时保留用于调试
4. **索引一致性**：同 templateId+version 会替换旧条目
5. **并发安全**：使用锁机制避免并发安装冲突
