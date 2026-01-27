package com.mg.booth.api;

import com.mg.booth.camera.CameraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 相机预览控制 API
 */
@RestController
@RequestMapping("/api/v1/camera/preview")
public class CameraPreviewController {

    @Autowired
    @Qualifier("cameraAgentCameraService")
    private CameraService cameraService;

    /**
     * 重启预览（停止后重新启动）
     * POST /api/v1/camera/preview/restart
     * 用于前端预览卡住时的自愈恢复
     * 幂等操作：无论当前预览状态如何，都会先停止再启动
     */
    @PostMapping("/restart")
    public ResponseEntity<Map<String, Object>> restartPreview() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 先停止（幂等，如果未运行也不会报错）
            try {
                cameraService.stopPreview();
            } catch (Exception e) {
                // 忽略停止失败（可能预览本来就没运行）
            }
            
            // 等待一小段时间，确保停止完成
            Thread.sleep(100);
            
            // 再启动（幂等）
            cameraService.startPreview();
            
            response.put("ok", true);
            response.put("message", "Preview restarted");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("ok", false);
            response.put("error", ex.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
