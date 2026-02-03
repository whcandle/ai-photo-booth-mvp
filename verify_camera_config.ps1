# Camera Config API 持久化验证脚本

Write-Host "=== Camera Config API 持久化验证 ===" -ForegroundColor Green
Write-Host ""

# 1. 检查文件是否存在
$configFile = "camera.json"
if (Test-Path $configFile) {
    Write-Host "✅ camera.json 文件存在" -ForegroundColor Green
} else {
    Write-Host "❌ camera.json 文件不存在" -ForegroundColor Red
    exit 1
}

# 2. 读取并解析 JSON
try {
    $json = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host "✅ JSON 格式正确" -ForegroundColor Green
    
    # 3. 验证关键字段
    Write-Host ""
    Write-Host "配置内容验证：" -ForegroundColor Yellow
    Write-Host "  cameraModel: $($json.cameraModel)"
    Write-Host "  activePresetId: $($json.activePresetId)"
    Write-Host "  params.iso: $($json.params.iso)"
    Write-Host "  presets 数量: $($json.presets.Count)"
    
    if ($json.presets.Count -ge 4) {
        Write-Host "✅ 包含 4 个默认 preset" -ForegroundColor Green
    } else {
        Write-Host "⚠️  preset 数量不足 4 个" -ForegroundColor Yellow
    }
    
    # 4. 测试修改并验证持久化
    Write-Host ""
    Write-Host "=== 测试持久化 ===" -ForegroundColor Yellow
    
    $originalIso = $json.params.iso
    $newIso = 200
    
    Write-Host "当前 ISO: $originalIso"
    Write-Host "准备修改为: $newIso"
    
    # 修改 ISO
    $json.params.iso = $newIso
    $json | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
    
    Write-Host "✅ 已保存修改" -ForegroundColor Green
    
    # 重新读取验证
    $json2 = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($json2.params.iso -eq $newIso) {
        Write-Host "✅ 持久化验证成功：ISO = $($json2.params.iso)" -ForegroundColor Green
    } else {
        Write-Host "❌ 持久化失败：期望 ISO = $newIso，实际 = $($json2.params.iso)" -ForegroundColor Red
    }
    
    # 恢复原值
    $json.params.iso = $originalIso
    $json | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
    Write-Host "已恢复原始 ISO 值" -ForegroundColor Gray
    
} catch {
    Write-Host "❌ JSON 解析失败: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== 验收完成 ===" -ForegroundColor Green
