package com.mg.booth.camera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Service("cameraAgentCameraService")
public class CameraAgentCameraService implements CameraService {

    private static final Logger log = LoggerFactory.getLogger(CameraAgentCameraService.class);

    private final ObjectMapper om = new ObjectMapper();

    @Value("${booth.cameraAgentBaseUrl:http://127.0.0.1:18080}")
    private String baseUrl;

    @Value("${booth.cameraAgentTimeoutMs:30000}")
    private int timeoutMs;

    @Value("${booth.cameraAgentCheckStatusBeforeCapture:true}")
    private boolean checkStatusBeforeCapture;

    @Override
    public void captureTo(Path targetFile) throws Exception {
        // Optional: Check camera status before capture (recommended)
        if (checkStatusBeforeCapture) {
            assertCameraReady();
        }

        // 1) 组装请求
        URL url = new URL(baseUrl + "/capture");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // 2) 传 targetFile（建议绝对路径）
        String body = "{\"targetFile\":\"" + escapeJson(targetFile.toAbsolutePath().toString()) + "\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        // 3) 解析响应
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        JsonNode resp = om.readTree(is);

        boolean ok = resp.path("ok").asBoolean(false);
        if (!ok) {
            String err = resp.path("error").asText("unknown");
            int errorCode = resp.path("errorCode").asInt(0);
            throw new RuntimeException("CameraAgent capture failed: http=" + code
                + ", errorCode=" + errorCode + ", error=" + err);
        }

        // Log capture metrics for debugging
        long size = resp.path("size").asLong(-1);
        long elapsedMs = resp.path("elapsedMs").asLong(-1);
        String path = resp.path("path").asText(null);
        log.info("CameraAgent capture ok: elapsedMs={}, size={}, path={}", elapsedMs, size, path);
    }

    @Override
    public CameraStatus getStatus() throws Exception {
        URL url = new URL(baseUrl + "/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        JsonNode resp = om.readTree(is);

        CameraStatus s = new CameraStatus();
        s.ok = resp.path("ok").asBoolean(false);
        s.cameraConnected = resp.path("cameraConnected").asBoolean(false);
        s.error = resp.path("error").asText(null);
        s.cameraThreadId = resp.has("cameraThreadId") && !resp.path("cameraThreadId").isNull()
            ? resp.path("cameraThreadId").asInt() : null;
        s.apartmentState = resp.path("apartmentState").asText(null);
        s.queueLength = resp.has("queueLength") && !resp.path("queueLength").isNull()
            ? resp.path("queueLength").asInt() : null;
        s.sdkInitialized = resp.has("sdkInitialized") && !resp.path("sdkInitialized").isNull()
            ? resp.path("sdkInitialized").asBoolean(false) : null;
        s.sessionOpened = resp.has("sessionOpened") && !resp.path("sessionOpened").isNull()
            ? resp.path("sessionOpened").asBoolean(false) : null;
        return s;
    }

    /**
     * Check if camera is ready before capture.
     * Throws exception if camera is not ready.
     */
    private void assertCameraReady() throws Exception {
        CameraStatus status = getStatus();
        if (!status.ok || !status.cameraConnected) {
            String err = status.error != null ? status.error : "camera not ready";
            throw new RuntimeException("CameraAgent not ready: error=" + err
                + ", sdkInitialized=" + status.sdkInitialized
                + ", sessionOpened=" + status.sessionOpened);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
