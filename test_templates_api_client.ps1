# Test script for PlatformDeviceApiClient.listActivityTemplates()
# This script tests the Platform API endpoint directly (before integrating into DevicePlatformController)

param(
    [String]$platformUrl = "http://127.0.0.1:8089",
    [Long]$deviceId = 1,
    [Long]$activityId = 1,
    [String]$deviceToken = ""
)

Write-Host "=== Testing PlatformDeviceApiClient.listActivityTemplates() ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Platform URL: $platformUrl" -ForegroundColor Gray
Write-Host "Device ID: $deviceId" -ForegroundColor Gray
Write-Host "Activity ID: $activityId" -ForegroundColor Gray
Write-Host ""

# Try to get token and deviceId from device.json
if (Test-Path "device.json") {
    try {
        $deviceConfig = Get-Content "device.json" | ConvertFrom-Json
        
        # Load token if not provided
        if ([string]::IsNullOrEmpty($deviceToken)) {
            Write-Host "Token not provided, trying to read from device.json..." -ForegroundColor Yellow
            $deviceToken = $deviceConfig.deviceToken
            if ($deviceToken) {
                Write-Host "   [OK] Token loaded from device.json" -ForegroundColor Green
            } else {
                Write-Host "   [ERROR] deviceToken not found in device.json" -ForegroundColor Red
                Write-Host "   Please run handshake first or provide token via -deviceToken parameter" -ForegroundColor Yellow
                exit 1
            }
        }
        
        # Load deviceId if not provided or if it's the default value
        if ($deviceId -eq 1 -or $null -eq $deviceId) {
            $configDeviceId = $deviceConfig.deviceId
            if ($configDeviceId) {
                try {
                    $deviceId = [Long]$configDeviceId
                    Write-Host "Device ID not specified, using deviceId from device.json: $deviceId" -ForegroundColor Yellow
                } catch {
                    Write-Host "   [WARN] Failed to parse deviceId from device.json, using default: $deviceId" -ForegroundColor Yellow
                }
            }
        }
    } catch {
        Write-Host "   [ERROR] Failed to parse device.json: $_" -ForegroundColor Red
        if ([string]::IsNullOrEmpty($deviceToken)) {
            Write-Host "   Please provide token via -deviceToken parameter" -ForegroundColor Yellow
            exit 1
        }
    }
} else {
    if ([string]::IsNullOrEmpty($deviceToken)) {
        Write-Host "   [ERROR] device.json not found" -ForegroundColor Red
        Write-Host "   Please run handshake first or provide token via -deviceToken parameter" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host ""

# Build URL
$url = "$platformUrl/api/v1/device/$deviceId/activities/$activityId/templates"
Write-Host "Request URL: $url" -ForegroundColor Yellow
Write-Host ""

# Set headers
$headers = @{
    "Authorization" = "Bearer $deviceToken"
    "Content-Type" = "application/json"
}

Write-Host "1. Testing GET request to Platform API..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri $url -Method GET -Headers $headers -UseBasicParsing -ErrorAction Stop
    
    Write-Host "   [OK] Request successful (Status: $($response.StatusCode))" -ForegroundColor Green
    Write-Host ""
    
    $json = $response.Content | ConvertFrom-Json
    
    # Check response structure
    if ($json.PSObject.Properties.Name -contains "success") {
        if ($json.success -eq $true) {
            Write-Host "   [OK] API returned success=true" -ForegroundColor Green
            Write-Host ""
            
            # Check data field
            if ($json.PSObject.Properties.Name -contains "data") {
                $data = $json.data
                
                if ($null -eq $data) {
                    Write-Host "   [INFO] data is null (empty list)" -ForegroundColor Cyan
                    Write-Host "   This is valid - API Client should return empty list" -ForegroundColor Gray
                } elseif ($data -is [Array]) {
                    $count = $data.Count
                    Write-Host "   [OK] data is an array with $count item(s)" -ForegroundColor Green
                    Write-Host ""
                    
                    # Display first few items
                    if ($count -gt 0) {
                        Write-Host "   Sample items:" -ForegroundColor Cyan
                        $displayCount = [Math]::Min(3, $count)
                        for ($i = 0; $i -lt $displayCount; $i++) {
                            Write-Host "   Item #$($i+1):" -ForegroundColor Yellow
                            $item = $data[$i]
                            if ($item.PSObject.Properties.Name -contains "templateId") {
                                Write-Host "     templateId: $($item.templateId)" -ForegroundColor Gray
                            }
                            if ($item.PSObject.Properties.Name -contains "name") {
                                Write-Host "     name: $($item.name)" -ForegroundColor Gray
                            }
                            if ($item.PSObject.Properties.Name -contains "version") {
                                Write-Host "     version: $($item.version)" -ForegroundColor Gray
                            }
                            if ($item.PSObject.Properties.Name -contains "updatedAt") {
                                Write-Host "     updatedAt: $($item.updatedAt)" -ForegroundColor Gray
                            }
                            Write-Host ""
                        }
                        
                        # Check if all items have updatedAt
                        $allHaveUpdatedAt = $true
                        foreach ($item in $data) {
                            if (-not ($item.PSObject.Properties.Name -contains "updatedAt")) {
                                $allHaveUpdatedAt = $false
                                break
                            }
                        }
                        if ($allHaveUpdatedAt) {
                            Write-Host "   [OK] All templates have updatedAt field" -ForegroundColor Green
                        } else {
                            Write-Host "   [WARN] Some templates are missing updatedAt field" -ForegroundColor Yellow
                        }
                    }
                } else {
                    Write-Host "   [WARN] data is not an array: $($data.GetType().Name)" -ForegroundColor Yellow
                }
            } else {
                Write-Host "   [WARN] Response missing 'data' field" -ForegroundColor Yellow
            }
            
            Write-Host ""
            Write-Host "   Full response:" -ForegroundColor Cyan
            $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
            
        } else {
            Write-Host "   [ERROR] API returned success=false" -ForegroundColor Red
            Write-Host "   message: $($json.message)" -ForegroundColor Red
            Write-Host ""
            Write-Host "   This should throw PlatformCallException in API Client" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   [WARN] Response missing 'success' field" -ForegroundColor Yellow
        Write-Host "   Full response:" -ForegroundColor Cyan
        $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
    }
    
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "   [ERROR] Request failed (Status: $statusCode)" -ForegroundColor Red
    Write-Host "   Error: $_" -ForegroundColor Red
    Write-Host ""
    
    if ($statusCode -eq 401) {
        Write-Host "   [INFO] 401 Unauthorized - This should throw PlatformCallException with reason='unauthorized'" -ForegroundColor Yellow
        Write-Host "   Expected behavior: API Client throws PlatformCallException.isUnauthorized() == true" -ForegroundColor Gray
    } elseif ($statusCode -eq 404) {
        Write-Host "   [INFO] 404 Not Found - Activity or device not found" -ForegroundColor Yellow
    } elseif ($statusCode -eq 403) {
        Write-Host "   [INFO] 403 Forbidden - Device does not have access to this activity" -ForegroundColor Yellow
    } else {
        Write-Host "   [INFO] HTTP $statusCode - This should throw PlatformCallException with reason='http_error'" -ForegroundColor Yellow
    }
    
    # Try to parse error response
    try {
        $errorMessage = $_.ErrorDetails.Message
        if ($null -ne $errorMessage -and $errorMessage -ne "") {
            try {
                $errorResponse = $errorMessage | ConvertFrom-Json
                Write-Host ""
                Write-Host "   Error response:" -ForegroundColor Cyan
                $errorMessage | ConvertFrom-Json | ConvertTo-Json -Depth 5
            } catch {
                Write-Host ""
                Write-Host "   Error response (raw): $errorMessage" -ForegroundColor Cyan
            }
        } else {
            # Try to get response body from exception
            try {
                $response = $_.Exception.Response
                if ($null -ne $response) {
                    $stream = $response.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($stream)
                    $responseBody = $reader.ReadToEnd()
                    $reader.Close()
                    $stream.Close()
                    
                    if ($null -ne $responseBody -and $responseBody -ne "") {
                        Write-Host ""
                        Write-Host "   Error response body:" -ForegroundColor Cyan
                        try {
                            $responseBody | ConvertFrom-Json | ConvertTo-Json -Depth 5
                        } catch {
                            Write-Host "   $responseBody" -ForegroundColor Gray
                        }
                    }
                }
            } catch {
                Write-Host "   (Could not extract error response)" -ForegroundColor Gray
            }
        }
    } catch {
        Write-Host "   (Could not parse error response)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Verify API Client method handles all response cases correctly" -ForegroundColor White
Write-Host "  2. Test with invalid token (should get 401)" -ForegroundColor White
Write-Host "  3. Test with unreachable platform (should get 503/unreachable)" -ForegroundColor White
Write-Host "  4. Integrate into DevicePlatformController" -ForegroundColor White
