# AI Photo Booth MVP - Day1

## Run
- JDK 17
- Maven
- IDEA run `AiPhotoBoothApplication`

## APIs
- GET  http://localhost:8080/api/v1/health
- GET  http://localhost:8080/api/v1/templates
- POST http://localhost:8080/api/v1/sessions
- GET  http://localhost:8080/api/v1/sessions/{sessionId}

## Create session example
POST /api/v1/sessions
```json
{
  "deviceId": "kiosk-001",
  "countdownSeconds": 3,
  "maxRetries": 2
}
```

## Test with curl

### 创建 Session
```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"kiosk-001","countdownSeconds":3,"maxRetries":2}'
```

### 查询 Session
```bash
curl http://localhost:8080/api/v1/sessions/sess_xxx
```

### 模板列表
```bash
curl http://localhost:8080/api/v1/templates
```

### Health Check
```bash
curl http://localhost:8080/api/v1/health
```
