package com.mg.booth.api;

import com.mg.booth.camera.CameraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 相机属性设置 API（供前端直接设置单个属性）
 */
@RestController
@RequestMapping("/api/v1/camera/properties")
public class CameraPropertyController {

    @Autowired
    @Qualifier("cameraAgentCameraService")
    private CameraService cameraService;

    /**
     * 设置单个相机属性
     * POST /api/v1/camera/properties/set
     * Body: { "key": "ISO", "value": 144, "persist": false }
     */
    @PostMapping("/set")
    public ResponseEntity<Map<String, Object>> setProperty(@RequestBody SetPropertyRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        if (request == null || request.getKey() == null || request.getValue() == null) {
            response.put("ok", false);
            response.put("error", "key and value are required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            boolean persist = request.getPersist() != null ? request.getPersist() : false;
            cameraService.setProperty(request.getKey(), request.getValue(), persist);
            
            response.put("ok", true);
            response.put("key", request.getKey());
            response.put("value", request.getValue());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("ok", false);
            response.put("error", ex.getMessage());
            response.put("key", request.getKey());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 请求 DTO
     */
    public static class SetPropertyRequest {
        private String key;
        private Integer value;
        private Boolean persist;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }

        public Boolean getPersist() {
            return persist;
        }

        public void setPersist(Boolean persist) {
            this.persist = persist;
        }
    }
}
