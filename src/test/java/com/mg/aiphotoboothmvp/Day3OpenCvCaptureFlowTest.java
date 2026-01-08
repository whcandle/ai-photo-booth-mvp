package com.mg.aiphotoboothmvp;

import com.mg.booth.AiPhotoBoothApplication;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 关键改动：加上 classes = AiPhotoBoothMvpApplication.class（替换成你的主启动类名）
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = AiPhotoBoothApplication.class // 核心：指定Spring Boot主启动类
)
public class Day3OpenCvCaptureFlowTest {

    @LocalServerPort
    int port;

    private final RestTemplate rt = new RestTemplate();

    private String base() {
        return "http://localhost:" + port;
    }

    /**
     * ✅ Day3（OpenCV 摄像头）验收标准：
     * 1) create -> SELECTING
     * 2) selectTemplate -> COUNTDOWN
     * 3) capture -> CAPTURING/PROCESSING
     * 4) 轮询直到 rawUrl != null（并且 /files/raw/... 可访问）
     */
    @Test
    void day3_openCv_capture_should_generate_rawUrl_and_file_accessible() throws Exception {
        // 0) 先验证 OpenCV native 是否能加载（如果你用 System.loadLibrary）
        //    如果你在 UsbCameraService 里是 System.load("D:\\...dll")，这步也能帮你提前发现位数/路径问题
        tryLoadOpenCv();

        // 1) create
        var createReq = Map.of("deviceId", "kiosk-001", "countdownSeconds", 3, "maxRetries", 2);
        var createResp = postJson("/api/v1/sessions", createReq);
        String sessionId = (String) createResp.get("sessionId");
        assertNotNull(sessionId);
        assertEquals("SELECTING", createResp.get("state"));

        // 2) templates & choose first enabled
        var tplResp = getJson("/api/v1/templates");
        List<Map<String, Object>> items = (List<Map<String, Object>>) tplResp.get("items");
        assertNotNull(items);
        assertTrue(items.size() > 0, "templates should not be empty");
        String templateId = (String) items.get(0).get("templateId");
        assertNotNull(templateId);

        // 3) select template -> COUNTDOWN
        var selResp = postJson("/api/v1/sessions/" + sessionId + "/template", Map.of("templateId", templateId));
        assertEquals("COUNTDOWN", selResp.get("state"));

        // 4) capture
        Integer attemptIndex = (Integer) selResp.get("attemptIndex");
        var capResp = postJson("/api/v1/sessions/" + sessionId + "/capture", Map.of("attemptIndex", attemptIndex));
        assertNotNull(capResp.get("state"));

        // 5) poll until rawUrl appears (Day3 的核心验收点)
        Map<String, Object> last = null;
        String rawUrl = null;

        for (int i = 0; i < 60; i++) { // 60*0.8s ≈ 48s
            Thread.sleep(800);
            last = getJson("/api/v1/sessions/" + sessionId);
            String st = (String) last.get("state");
            rawUrl = (String) last.get("rawUrl");

            if (rawUrl != null && !rawUrl.isBlank()) {
                break;
            }

            // 如果你有 sweeper，可能会回 IDLE；那就直接失败，让你看到原因
            if ("IDLE".equals(st)) {
                fail("Session returned to IDLE before rawUrl generated. last=" + last);
            }
        }

        assertNotNull(last);
        assertNotNull(rawUrl, "rawUrl should be generated after capture");
        assertTrue(rawUrl.startsWith("/files/raw/"), "rawUrl format unexpected: " + rawUrl);

        // 6) verify raw file endpoint accessible
        ResponseEntity<byte[]> fileResp = rt.getForEntity(base() + rawUrl, byte[].class);
        assertEquals(HttpStatus.OK, fileResp.getStatusCode());
        assertNotNull(fileResp.getBody());
        assertTrue(fileResp.getBody().length > 10_000, "raw jpg too small? size=" + fileResp.getBody().length);

        // 打印一下方便你看
        System.out.println("✅ Day3 OK. sessionId=" + sessionId + " rawUrl=" + rawUrl);
    }

    private void tryLoadOpenCv() {
        try {
            // 如果你是 System.loadLibrary(Core.NATIVE_LIBRARY_NAME)，这里就能验证 DLL 是否匹配
            Class<?> core = Class.forName("org.opencv.core.Core");
            String libName = (String) core.getField("NATIVE_LIBRARY_NAME").get(null);
            System.loadLibrary(libName);
            String version = (String) core.getField("VERSION").get(null);
            System.out.println("OpenCV loaded, version=" + version);
        } catch (UnsatisfiedLinkError e) {
            fail("OpenCV native dll load failed: " + e.getMessage()
                    + "\nCheck: x64 dll vs x64 JDK, and -Djava.library.path/native folder.");
        } catch (Exception e) {
            fail("OpenCV class not found or load failed: " + e.getMessage());
        }
    }

    private Map<String, Object> postJson(String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, h);
        ResponseEntity<Map> resp = rt.exchange(base() + path, HttpMethod.POST, entity, Map.class);

        assertTrue(resp.getStatusCode().is2xxSuccessful(), "POST " + path + " failed: " + resp.getStatusCode());
        return resp.getBody();
    }

    private Map<String, Object> getJson(String path) {
        ResponseEntity<Map> resp = rt.getForEntity(base() + path, Map.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(), "GET " + path + " failed: " + resp.getStatusCode());
        return resp.getBody();
    }
}