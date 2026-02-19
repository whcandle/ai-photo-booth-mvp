# Test script for template package installation
# Usage: .\test_template_install.ps1 -templateId <id> -version <version> -downloadUrl <url> -checksum <sha256>

param(
    [Parameter(Mandatory=$true)]
    [Long]$templateId,
    
    [Parameter(Mandatory=$true)]
    [String]$version,
    
    [Parameter(Mandatory=$true)]
    [String]$downloadUrl,
    
    [Parameter(Mandatory=$true)]
    [String]$checksum,
    
    [Long]$activityId = $null,
    
    [String]$baseUrl = "http://127.0.0.1:8080"
)

Write-Host "=== Testing Template Package Installation ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Template ID: $templateId" -ForegroundColor Gray
Write-Host "Version: $version" -ForegroundColor Gray
Write-Host "Download URL: $downloadUrl" -ForegroundColor Gray
Write-Host "Checksum: $checksum" -ForegroundColor Gray
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

# Prepare request body
$requestBody = @{
    templateId = $templateId
    version = $version
    downloadUrl = $downloadUrl
    checksum = $checksum
} | ConvertTo-Json

if ($activityId) {
    $requestBodyObj = $requestBody | ConvertFrom-Json
    $requestBodyObj | Add-Member -MemberType NoteProperty -Name "activityId" -Value $activityId
    $requestBody = $requestBodyObj | ConvertTo-Json
}

Write-Host "2. Installing template package..." -ForegroundColor Yellow
Write-Host "   Request body:" -ForegroundColor Gray
$requestBody | ConvertFrom-Json | ConvertTo-Json | Write-Host -ForegroundColor Gray
Write-Host ""

try {
    $headers = @{
        "Content-Type" = "application/json"
    }
    
    $response = Invoke-WebRequest -Uri "$baseUrl/local/device/templates/install" `
        -Method POST `
        -Headers $headers `
        -Body $requestBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Host "   [OK] Request successful (Status: $($response.StatusCode))" -ForegroundColor Green
    Write-Host ""
    
    $result = $response.Content | ConvertFrom-Json
    
    if ($result.success) {
        Write-Host "   [OK] Installation successful" -ForegroundColor Green
        Write-Host "   installedPath: $($result.data.installedPath)" -ForegroundColor Gray
        Write-Host "   indexUpdated: $($result.data.indexUpdated)" -ForegroundColor Gray
        Write-Host ""
        
        # Verify installation
        Write-Host "3. Verifying installation..." -ForegroundColor Yellow
        
        $dataDir = "data"
        if (Test-Path $dataDir) {
            $installedPath = $result.data.installedPath
            $fullPath = Join-Path $dataDir $installedPath
            $manifestPath = Join-Path $fullPath "manifest.json"
            
            if (Test-Path $manifestPath) {
                Write-Host "   [OK] manifest.json exists at: $manifestPath" -ForegroundColor Green
                try {
                    $manifest = Get-Content $manifestPath | ConvertFrom-Json
                    Write-Host "   manifest.templateId: $($manifest.templateId)" -ForegroundColor Gray
                    Write-Host "   manifest.version: $($manifest.version)" -ForegroundColor Gray
                } catch {
                    Write-Host "   [WARN] Failed to parse manifest.json: $_" -ForegroundColor Yellow
                }
            } else {
                Write-Host "   [ERROR] manifest.json not found at: $manifestPath" -ForegroundColor Red
            }
        } else {
            Write-Host "   [WARN] data directory not found" -ForegroundColor Yellow
        }
        
        Write-Host ""
        
        # Check index.json
        Write-Host "4. Checking index.json..." -ForegroundColor Yellow
        $indexPath = Join-Path $dataDir "index.json"
        if (Test-Path $indexPath) {
            Write-Host "   [OK] index.json exists" -ForegroundColor Green
            try {
                $index = Get-Content $indexPath | ConvertFrom-Json
                Write-Host "   schemaVersion: $($index.schemaVersion)" -ForegroundColor Gray
                Write-Host "   items count: $($index.items.Count)" -ForegroundColor Gray
                
                $item = $index.items | Where-Object { $_.templateId -eq $templateId -and $_.version -eq $version }
                if ($item) {
                    Write-Host "   [OK] Template found in index" -ForegroundColor Green
                    Write-Host "   path: $($item.path)" -ForegroundColor Gray
                    Write-Host "   installedAt: $($item.installedAt)" -ForegroundColor Gray
                    Write-Host "   checksum: $($item.checksum)" -ForegroundColor Gray
                } else {
                    Write-Host "   [WARN] Template not found in index" -ForegroundColor Yellow
                }
            } catch {
                Write-Host "   [WARN] Failed to parse index.json: $_" -ForegroundColor Yellow
            }
        } else {
            Write-Host "   [WARN] index.json not found" -ForegroundColor Yellow
        }
        
        Write-Host ""
        Write-Host "Full response:" -ForegroundColor Cyan
        $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
        
    } else {
        Write-Host "   [ERROR] Installation failed: $($result.message)" -ForegroundColor Red
        exit 1
    }
    
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "   [ERROR] Request failed (Status: $statusCode)" -ForegroundColor Red
    Write-Host "   Error: $_" -ForegroundColor Red
    
    # Try to parse error response
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host ""
        Write-Host "   Error response:" -ForegroundColor Red
        $errorBody | ConvertFrom-Json | ConvertTo-Json -Depth 5
    } catch {
        Write-Host "   (Could not parse error response)" -ForegroundColor Gray
    }
    
    exit 1
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Verification Checklist:" -ForegroundColor Yellow
Write-Host "  [ ] Installation returned success=true" -ForegroundColor White
Write-Host "  [ ] data/templates/<templateId>/<version>/manifest.json exists" -ForegroundColor White
Write-Host "  [ ] index.json updated with new entry" -ForegroundColor White
Write-Host "  [ ] No tmp files left in final directory" -ForegroundColor White
