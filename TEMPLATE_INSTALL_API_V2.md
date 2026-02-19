# 模板安装 API v2 - templateCode + versionSemver

## 变更概述

模板安装链路已与 manifest 对齐，使用 `templateCode` + `versionSemver` 替代 numeric `templateId` + `version`。

## API 变更

### 新字段（推荐）

- `templateCode` (String): 模板代码，例如 "tpl_001"
- `versionSemver` (String): 版本号（semver 格式），例如 "0.1.0"
- `checksumSha256` (String): SHA256 校验和

### 旧字段（兼容，已废弃）

- `templateId` (String): 已废弃，请使用 `templateCode`
- `version` (String): 已废弃，请使用 `versionSemver`
- `checksum` (String): 已废弃，请使用 `checksumSha256`

## 安装请求示例

### Windows CMD 版本（使用 curl）

```cmd
curl -X POST http://127.0.0.1:8080/local/device/templates/install ^
  -H "Content-Type: application/json" ^
  -d "{\"templateCode\":\"tpl_001\",\"versionSemver\":\"0.1.0\",\"downloadUrl\":\"https://example.com/packages/tpl_001-0.1.0.zip\",\"checksumSha256\":\"a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456\"}"
```

### PowerShell 版本

```powershell
$body = @{
    templateCode = "tpl_001"
    versionSemver = "0.1.0"
    downloadUrl = "https://example.com/packages/tpl_001-0.1.0.zip"
    checksumSha256 = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://127.0.0.1:8080/local/device/templates/install" `
  -Method POST `
  -ContentType "application/json" `
  -Body $body
```

### 兼容旧格式（已废弃，会输出警告日志）

```cmd
curl -X POST http://127.0.0.1:8080/local/device/templates/install ^
  -H "Content-Type: application/json" ^
  -d "{\"templateId\":\"tpl_001\",\"version\":\"0.1.0\",\"downloadUrl\":\"https://example.com/packages/tpl_001-0.1.0.zip\",\"checksum\":\"a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456\"}"
```

## 安装成功后的磁盘结构

### 目录结构

```
data/
├── templates/
│   └── tpl_001/
│       └── 0.1.0/
│           ├── manifest.json
│           ├── preview.png
│           ├── flow.json
│           ├── ai.json
│           ├── rules.json
│           └── ui/
│               └── intro.md
├── index.json
└── tmp/
    ├── downloads/
    └── staging/
```

### index.json 示例

```json
{
  "schemaVersion": 2,
  "updatedAt": "2026-02-04T12:00:00.000Z",
  "items": [
    {
      "templateId": "tpl_001",
      "version": "0.1.0",
      "path": "templates/tpl_001/0.1.0",
      "installedAt": "2026-02-04T12:00:00.000Z",
      "checksum": "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456",
      "downloadUrl": "https://example.com/packages/tpl_001-0.1.0.zip"
    }
  ]
}
```

**注意**：
- `schemaVersion`: 2（新版本）
- `templateId`: 存储的是 `templateCode`（String，如 "tpl_001"）
- `version`: 存储的是 `versionSemver`（String，如 "0.1.0"）
- `path`: 格式为 `templates/<templateCode>/<versionSemver>`

## Manifest 校验规则

安装时会校验 `manifest.json`：

1. **templateId 字段**：必须等于请求中的 `templateCode`（字符串完全匹配）
2. **version 字段**：必须等于请求中的 `versionSemver`（字符串完全匹配）

### manifest.json 示例

```json
{
  "templateId": "tpl_001",
  "version": "0.1.0",
  "name": "模板名称",
  "description": "模板描述"
}
```

## 安装日志示例

### Install Dry-Run 日志

```
[tpl-install] Install dry-run: templateCode=tpl_001, versionSemver=0.1.0, downloadUrl=https://example.com/packages/tpl_001-0.1.0.zip, checksumSha256=a1b2c3d4e5f6...
```

### 完整安装流程日志

```
[tpl-install] Starting installation: templateCode=tpl_001, versionSemver=0.1.0, downloadUrl=https://example.com/packages/tpl_001-0.1.0.zip
[tpl-install] Step=download: templateCode=tpl_001, versionSemver=0.1.0, url=https://example.com/packages/tpl_001-0.1.0.zip
[tpl-install] Step=download-complete: templateCode=tpl_001, versionSemver=0.1.0, size=12345
[tpl-install] Step=verify: templateCode=tpl_001, versionSemver=0.1.0
[tpl-install] Step=verify-complete: templateCode=tpl_001, versionSemver=0.1.0, checksum=a1b2c3d4e5f6...
[tpl-install] Step=unzip: templateCode=tpl_001, versionSemver=0.1.0, staging=.../tmp/staging/tpl_001/0.1.0_<uuid>
[tpl-install] Step=unzip-complete: templateCode=tpl_001, versionSemver=0.1.0
[tpl-install] Step=validate-manifest: templateCode=tpl_001, versionSemver=0.1.0
[tpl-install] Step=validate-manifest-complete: templateCode=tpl_001, versionSemver=0.1.0
[tpl-install] Step=commit: templateCode=tpl_001, versionSemver=0.1.0, finalDir=.../data/templates/tpl_001/0.1.0
[tpl-install] Step=commit-complete: templateCode=tpl_001, versionSemver=0.1.0, finalDir=.../data/templates/tpl_001/0.1.0
[tpl-install] Step=update-index: templateCode=tpl_001, versionSemver=0.1.0
[tpl-install] Step=update-index-complete: templateCode=tpl_001, versionSemver=0.1.0
[tpl-install] Installation complete: templateCode=tpl_001, versionSemver=0.1.0, path=templates/tpl_001/0.1.0
```

## 响应示例

### 成功响应

```json
{
  "success": true,
  "data": {
    "installedPath": "templates/tpl_001/0.1.0",
    "indexUpdated": true
  },
  "message": null
}
```

### 错误响应

```json
{
  "success": false,
  "data": null,
  "message": "Installation failed: manifest.json templateId mismatch: expected=tpl_001, actual=tpl_002"
}
```

## 兼容性说明

### Schema Version 迁移

- **Schema Version 1**（旧版）：`templateId` 可能是 numeric ID 的字符串形式
- **Schema Version 2**（新版）：`templateId` 存储 `templateCode`（String）

读取旧版 index.json 时会自动迁移到 schemaVersion 2，无需手动操作。

### 向后兼容

- 旧字段（`templateId`/`version`/`checksum`）仍可使用，但会输出警告日志
- 建议尽快迁移到新字段（`templateCode`/`versionSemver`/`checksumSha256`）

## 验证清单

安装成功后，验证以下内容：

- [ ] 目录结构：`data/templates/<templateCode>/<versionSemver>/` 存在
- [ ] manifest.json 存在且内容正确
- [ ] 必需文件存在：`rules.json`、`preview.png`
- [ ] index.json 已更新，包含新安装的模板
- [ ] index.json 的 `schemaVersion` 为 2
- [ ] index.json 的 `templateId` 字段存储的是 `templateCode`（String）
- [ ] index.json 的 `version` 字段存储的是 `versionSemver`（String）
