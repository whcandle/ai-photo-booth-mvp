package com.mg.booth.api;

import com.mg.booth.domain.CameraProfile;
import com.mg.booth.service.CameraProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 相机策略配置 API
 */
@RestController
@RequestMapping("/api/v1/camera/profiles")
public class CameraProfileController {

    @Autowired
    private CameraProfileService profileService;

    /**
     * 获取所有可用策略列表
     * GET /api/v1/camera/profiles
     */
    @GetMapping
    public ResponseEntity<List<CameraProfile>> listProfiles() {
        List<CameraProfile> profiles = profileService.listProfiles();
        return ResponseEntity.ok(profiles);
    }

    /**
     * 根据ID获取策略详情
     * GET /api/v1/camera/profiles/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CameraProfile> getProfile(@PathVariable String id) {
        CameraProfile profile = profileService.getProfile(id);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    /**
     * 应用策略到相机
     * POST /api/v1/camera/profiles/{id}/apply?persist=true
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<CameraProfileService.ApplyProfileResult> applyProfile(
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean persist) {
        try {
            CameraProfileService.ApplyProfileResult result = profileService.applyProfile(id, persist);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            // 返回部分失败的结果
            CameraProfileService.ApplyProfileResult errorResult = new CameraProfileService.ApplyProfileResult();
            errorResult.setProfileId(id);
            errorResult.setSuccess(false);
            errorResult.getFailedProps().put("_error", ex.getMessage());
            return ResponseEntity.ok(errorResult);
        }
    }
}
