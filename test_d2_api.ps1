# D2 Local Proxy API - Complete Test Script

$baseUrl = "http://127.0.0.1:8080"
$platformUrl = "http://127.0.0.1:8089"

Write-Host "=== D2 Local Proxy API Test ===" -ForegroundColor Cyan
Write-Host ""

# Check if application is running
Write-Host "Checking if application is running..." -ForegroundColor Yellow
try {
    $test = Invoke-WebRequest -Uri "$baseUrl/local/device/config" -Method GET -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    Write-Host "   [OK] Application is running" -ForegroundColor Green
} catch {
    Write-Host "   [ERROR] Application is not running. Please start: mvn spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 1. Test Handshake
Write-Host "1. Testing POST /local/device/handshake..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/handshake" `
        -Method POST -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        Write-Host "   [OK] Handshake successful" -ForegroundColor Green
        Write-Host "   deviceId: $($result.data.deviceId)" -ForegroundColor Gray
        Write-Host "   tokenExpiresAt: $($result.data.tokenExpiresAt)" -ForegroundColor Gray
    } else {
        Write-Host "   [ERROR] Handshake failed: $($result.message)" -ForegroundColor Red
    }
} catch {
    Write-Host "   [ERROR] Handshake request failed: $_" -ForegroundColor Red
}

Write-Host ""

# 2. Test Activities (Online)
Write-Host "2. Testing GET /local/device/activities (Online)..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/activities" `
        -Method GET -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        $stale = $result.data.stale
        $count = $result.data.items.Count
        if ($stale) {
            Write-Host "   [WARN] Using cached data (stale=true)" -ForegroundColor Yellow
            Write-Host "   cachedAt: $($result.data.cachedAt)" -ForegroundColor Gray
        } else {
            Write-Host "   [OK] Online data fetched successfully (stale=false)" -ForegroundColor Green
        }
        Write-Host "   items count: $count" -ForegroundColor Gray
    } else {
        Write-Host "   [ERROR] Activities fetch failed: $($result.message)" -ForegroundColor Red
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 503) {
        Write-Host "   [WARN] Platform unreachable (503)" -ForegroundColor Yellow
    } elseif ($statusCode -eq 401) {
        Write-Host "   [ERROR] Token invalid (401)" -ForegroundColor Red
    } else {
        Write-Host "   [ERROR] Request failed: $_" -ForegroundColor Red
    }
}

Write-Host ""

# 3. Check cache file
Write-Host "3. Checking cache file..." -ForegroundColor Yellow
if (Test-Path "activities_cache.json") {
    Write-Host "   [OK] Cache file exists" -ForegroundColor Green
    try {
        $cache = Get-Content "activities_cache.json" | ConvertFrom-Json
        Write-Host "   cachedAt: $($cache.cachedAt)" -ForegroundColor Gray
        Write-Host "   items count: $($cache.items.Count)" -ForegroundColor Gray
    } catch {
        Write-Host "   [WARN] Failed to parse cache file: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [WARN] Cache file does not exist" -ForegroundColor Yellow
}

Write-Host ""

# 4. Test Activities (Offline Fallback)
Write-Host "4. Testing GET /local/device/activities (Offline Fallback)..." -ForegroundColor Yellow
Write-Host "   Note: If platform is unreachable, should use cache" -ForegroundColor Gray

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/activities" `
        -Method GET -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        if ($result.data.stale) {
            Write-Host "   [OK] Offline fallback successful, using cache (stale=true)" -ForegroundColor Green
            Write-Host "   cachedAt: $($result.data.cachedAt)" -ForegroundColor Gray
            Write-Host "   items count: $($result.data.items.Count)" -ForegroundColor Gray
            Write-Host "   message: $($result.message)" -ForegroundColor Gray
        } else {
            Write-Host "   [INFO] Online data (stale=false)" -ForegroundColor Cyan
        }
    } else {
        Write-Host "   [ERROR] Activities fetch failed: $($result.message)" -ForegroundColor Red
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 503) {
        Write-Host "   [WARN] Platform unreachable and no cache (503)" -ForegroundColor Yellow
        try {
            $errorResponse = $_.ErrorDetails.Message | ConvertFrom-Json
            Write-Host "   message: $($errorResponse.message)" -ForegroundColor Gray
        } catch {
            Write-Host "   message: Platform unreachable and no cache" -ForegroundColor Gray
        }
    } elseif ($statusCode -eq 401) {
        Write-Host "   [ERROR] Token invalid (401)" -ForegroundColor Red
    } else {
        Write-Host "   [ERROR] Request failed: $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Checklist:" -ForegroundColor Yellow
Write-Host "  [ ] Handshake executed successfully" -ForegroundColor White
Write-Host "  [ ] Activities fetched online (stale=false)" -ForegroundColor White
Write-Host "  [ ] Activities fallback to cache (stale=true)" -ForegroundColor White
Write-Host "  [ ] Cache file created" -ForegroundColor White
Write-Host "  [ ] Error handling correct (401/503)" -ForegroundColor White
