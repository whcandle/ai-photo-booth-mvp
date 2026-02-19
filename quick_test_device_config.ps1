# Device.json Single Source of Truth - Quick Test Script (PowerShell)

Write-Host "=== Device.json Single Source of Truth - Quick Test ===" -ForegroundColor Cyan
Write-Host ""

# Check if application is running
$baseUrl = "http://127.0.0.1:8080"
$testUrl = "$baseUrl/local/device/config"

Write-Host "1. Checking if application is running..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri $testUrl -Method GET -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
    Write-Host "   [OK] Application is running" -ForegroundColor Green
} catch {
    Write-Host "   [ERROR] Application is not running. Please start: mvn spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "2. Reading current config..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri $testUrl -Method GET -UseBasicParsing
    $config = $response.Content | ConvertFrom-Json
    Write-Host "   [OK] Config read successfully" -ForegroundColor Green
    Write-Host "   platformBaseUrl: $($config.data.platformBaseUrl)" -ForegroundColor Gray
    Write-Host "   deviceCode: $($config.data.deviceCode)" -ForegroundColor Gray
    Write-Host "   deviceId: $($config.data.deviceId)" -ForegroundColor Gray
    Write-Host "   tokenExpiresAt: $($config.data.tokenExpiresAt)" -ForegroundColor Gray
} catch {
    Write-Host "   [ERROR] Failed to read config: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "3. Testing save config (preserve read-only fields)..." -ForegroundColor Yellow
$testConfig = @{
    platformBaseUrl = "http://test.example.com"
    deviceCode = "test_device_$(Get-Date -Format 'yyyyMMddHHmmss')"
    secret = "test_secret"
} | ConvertTo-Json

try {
    $headers = @{
        "Content-Type" = "application/json"
    }
    $response = Invoke-WebRequest -Uri $testUrl -Method PUT -Headers $headers -Body $testConfig -UseBasicParsing
    
    $result = $response.Content | ConvertFrom-Json
    if ($result.success) {
        Write-Host "   [OK] Config saved successfully" -ForegroundColor Green
        
        # Verify read-only fields are preserved
        $savedConfig = Invoke-WebRequest -Uri $testUrl -Method GET -UseBasicParsing
        $saved = $savedConfig.Content | ConvertFrom-Json
        
        if ($saved.data.deviceId -or $saved.data.deviceToken) {
            Write-Host "   [OK] Read-only fields (deviceId/token) preserved" -ForegroundColor Green
        } else {
            Write-Host "   [WARN] Read-only fields are empty (normal if handshake not executed)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   [ERROR] Config save failed: $($result.message)" -ForegroundColor Red
    }
} catch {
    Write-Host "   [ERROR] Failed to save config: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "4. Testing activities API (new endpoint)..." -ForegroundColor Yellow
$activitiesUrl = "$baseUrl/local/device/activities"
try {
    $response = Invoke-WebRequest -Uri $activitiesUrl -Method GET -UseBasicParsing -ErrorAction Stop
    $result = $response.Content | ConvertFrom-Json
    if ($result.items) {
        Write-Host "   [OK] Activities API is working (items: $($result.items.Count), stale: $($result.stale))" -ForegroundColor Green
    } else {
        Write-Host "   [WARN] Activities API returned unexpected format" -ForegroundColor Yellow
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host "   [WARN] Activities API returned 401 (token invalid, need handshake)" -ForegroundColor Yellow
    } elseif ($statusCode -eq 503) {
        Write-Host "   [WARN] Activities API returned 503 (platform unreachable, may use cache)" -ForegroundColor Yellow
    } else {
        Write-Host "   [WARN] Activities API call failed: $_" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "5. Checking device.json file..." -ForegroundColor Yellow
$deviceJsonPath = "device.json"
if (Test-Path $deviceJsonPath) {
    Write-Host "   [OK] device.json file exists" -ForegroundColor Green
    try {
        $content = Get-Content $deviceJsonPath -Raw | ConvertFrom-Json
        if ($content.deviceId) {
            Write-Host "   deviceId type: $($content.deviceId.GetType().Name)" -ForegroundColor Gray
        }
        Write-Host "   tokenExpiresAt format: $($content.tokenExpiresAt)" -ForegroundColor Gray
    } catch {
        Write-Host "   [WARN] Failed to parse device.json: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [WARN] device.json file does not exist (will be auto-created)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Key Checkpoints:" -ForegroundColor Yellow
Write-Host "  [ ] Application starts normally" -ForegroundColor White
Write-Host "  [ ] Config read works" -ForegroundColor White
Write-Host "  [ ] Config save works (preserves read-only fields)" -ForegroundColor White
Write-Host "  [ ] deviceId is String type (not Long)" -ForegroundColor White
Write-Host "  [ ] tokenExpiresAt is ISO8601 format" -ForegroundColor White
Write-Host ""
Write-Host "For detailed test guide, see: DEVICE_CONFIG_MIGRATION_TEST.md" -ForegroundColor Cyan
