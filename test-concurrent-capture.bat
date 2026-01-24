@echo off
echo ========================================
echo Concurrent Capture Test (MVP)
echo ========================================
echo.
echo This test verifies that concurrent capture requests
echo are queued and executed serially by CameraAgent.
echo.
echo Make sure:
echo   1. CameraAgent is running on http://127.0.0.1:18080
echo   2. MVP is running on http://localhost:8080
echo.
pause

set MVP_URL=http://localhost:8080
set AGENT_URL=http://127.0.0.1:18080

echo [1/3] Checking CameraAgent status...
curl -s %AGENT_URL%/status
echo.
echo.

echo [2/3] Checking MVP camera health...
curl -s %MVP_URL%/api/v1/health/camera
echo.
echo.

echo [3/3] Sending 3 concurrent capture requests...
echo This will take some time as requests are queued...
echo.

REM Create temp JSON files
echo {"targetFile":"D:\\AICreama\\booth\\data\\raw\\conc_test_1.jpg"} > %TEMP%\req1.json
echo {"targetFile":"D:\\AICreama\\booth\\data\\raw\\conc_test_2.jpg"} > %TEMP%\req2.json
echo {"targetFile":"D:\\AICreama\\booth\\data\\raw\\conc_test_3.jpg"} > %TEMP%\req3.json

REM Send 3 concurrent requests to CameraAgent
start /B curl -X POST %AGENT_URL%/capture -H "Content-Type: application/json" -d @%TEMP%\req1.json > %TEMP%\resp1.txt
start /B curl -X POST %AGENT_URL%/capture -H "Content-Type: application/json" -d @%TEMP%\req2.json > %TEMP%\resp2.txt
start /B curl -X POST %AGENT_URL%/capture -H "Content-Type: application/json" -d @%TEMP%\req3.json > %TEMP%\resp3.txt

echo Waiting for all requests to complete (max 3 minutes)...
timeout /t 180 /nobreak >nul

echo.
echo Response 1:
type %TEMP%\resp1.txt
echo.
echo Response 2:
type %TEMP%\resp2.txt
echo.
echo Response 3:
type %TEMP%\resp3.txt
echo.

echo ========================================
echo Test completed
echo ========================================
echo.
echo Check output files:
echo   D:\AICreama\booth\data\raw\conc_test_1.jpg
echo   D:\AICreama\booth\data\raw\conc_test_2.jpg
echo   D:\AICreama\booth\data\raw\conc_test_3.jpg
echo.
echo All 3 files should exist and have size > 100KB
echo.

pause
