# Test script for GET /local/device/activities/{activityId}/templates
# Tests online fetch, cache generation, and offline fallback

param(
    [String]$baseUrl = "http://127.0.0.1:8080",
    [Long]$activityId = 8
)

Write-Host "=== Testing Templates Endpoint ===" -ForegroundColor Cyan
Write-Host "Base URL: $baseUrl" -ForegroundColor Gray
Write-Host "Activity ID: $activityId" -ForegroundColor Gray
Write-Host ""

# Check if application is running
Write-Host "1. Checking if application is running..." -ForegroundColor Yellow
try {
    $test = Invoke-WebRequest -Uri "$baseUrl/local/device/config" -Method GET -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    Write-Host "   [OK] Application is running" -ForegroundColor Green
} catch {
    Write-Host "   [ERROR] Application is not running. Please start: mvn spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Test 1: Online fetch (should generate cache)
Write-Host "2. Testing GET /local/device/activities/$activityId/templates (Online)..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/activities/$activityId/templates" `
        -Method GET -UseBasicParsing -ErrorAction Stop
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
        
        if ($count -gt 0) {
            Write-Host "   First template:" -ForegroundColor Cyan
            Write-Host "     templateId: $($result.data.items[0].templateId)" -ForegroundColor Gray
            Write-Host "     name: $($result.data.items[0].name)" -ForegroundColor Gray
            if ($result.data.items[0].updatedAt) {
                Write-Host "     updatedAt: $($result.data.items[0].updatedAt)" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "   [ERROR] Templates fetch failed: $($result.message)" -ForegroundColor Red
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

# Check cache file
Write-Host "3. Checking cache file..." -ForegroundColor Yellow
$cacheFile = "templates_cache_$activityId.json"
if (Test-Path $cacheFile) {
    Write-Host "   [OK] Cache file exists: $cacheFile" -ForegroundColor Green
    try {
        $cache = Get-Content $cacheFile | ConvertFrom-Json
        Write-Host "   cachedAt: $($cache.cachedAt)" -ForegroundColor Gray
        Write-Host "   items count: $($cache.items.Count)" -ForegroundColor Gray
    } catch {
        Write-Host "   [WARN] Failed to parse cache file: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [WARN] Cache file does not exist: $cacheFile" -ForegroundColor Yellow
}

Write-Host ""

# Test 2: Offline fallback (stop platform or use wrong URL, then test)
Write-Host "4. Testing offline fallback..." -ForegroundColor Yellow
Write-Host "   Note: To test offline fallback, stop the Platform service (port 8089)" -ForegroundColor Gray
Write-Host "   Then run this script again, or manually test:" -ForegroundColor Gray
Write-Host "   Invoke-WebRequest -Uri `"$baseUrl/local/device/activities/$activityId/templates`" -UseBasicParsing" -ForegroundColor Gray

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Stop Platform service (port 8089)" -ForegroundColor White
Write-Host "  2. Run this script again to test offline fallback" -ForegroundColor White
Write-Host "  3. Verify response has stale=true and cachedAt field" -ForegroundColor White
