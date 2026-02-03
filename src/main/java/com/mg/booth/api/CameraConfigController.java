package com.mg.booth.api;

import com.mg.booth.camera.CameraConfig;
import com.mg.booth.camera.CameraConfigStore;
import com.mg.booth.camera.CameraParamsConverter;
import com.mg.booth.camera.CameraService;
import com.mg.booth.camera.CameraService.CameraStatus;
import com.mg.booth.config.BoothProps;
import com.mg.booth.service.CameraProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 相机配置 API Controller
 * 提供 camera.json 的读写接口
 * 限制：只允许 localhost 访问
 */
@RestController
@RequestMapping("/local/camera")
public class CameraConfigController {

  private static final Logger log = LoggerFactory.getLogger(CameraConfigController.class);

  private final BoothProps props;
  private final CameraConfigStore store;
  private final CameraService cameraService;
  private final CameraParamsConverter paramsConverter;
  private final CameraProfileService profileService;

  public CameraConfigController(
      BoothProps props,
      CameraConfigStore store,
      @Qualifier("cameraAgentCameraService") CameraService cameraService,
      CameraParamsConverter paramsConverter,
      CameraProfileService profileService) {
    this.props = props;
    this.store = store;
    this.cameraService = cameraService;
    this.paramsConverter = paramsConverter;
    this.profileService = profileService;
  }

  /**
   * 获取相机配置
   * GET /local/camera/config
   * 
   * @param request HTTP 请求（用于检查来源）
   * @return CameraConfig JSON
   */
  @GetMapping("/config")
  public ResponseEntity<?> getConfig(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      Path file = Path.of(props.getCameraConfigFile());
      CameraConfig config = store.load(file);
      return ResponseEntity.ok(config);
    } catch (Exception e) {
      log.error("[camera-config] Failed to load config: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(createErrorResponse("Failed to load config: " + e.getMessage()));
    }
  }

  /**
   * 保存相机配置
   * PUT /local/camera/config
   * 
   * @param config 配置对象（从请求体反序列化）
   * @param request HTTP 请求（用于检查来源）
   * @return 成功/失败响应
   */
  @PutMapping("/config")
  public ResponseEntity<?> saveConfig(
      @RequestBody CameraConfig config,
      HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      Path file = Path.of(props.getCameraConfigFile());
      store.save(file, config);
      
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "Config saved successfully");
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("[camera-config] Failed to save config: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(createErrorResponse("Failed to save config: " + e.getMessage()));
    }
  }

  /**
   * 获取相机状态
   * GET /local/camera/status
   * 
   * @param request HTTP 请求（用于检查来源）
   * @return 相机状态信息
   */
  @GetMapping("/status")
  public ResponseEntity<?> getStatus(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      CameraStatus status = cameraService.getStatus();
      
      Map<String, Object> data = new HashMap<>();
      data.put("connected", status.cameraConnected);
      data.put("cameraModel", getCameraModel());
      data.put("battery", null); // 暂不支持，后续可扩展
      data.put("lastError", status.error);
      data.put("ok", status.ok);
      data.put("sdkInitialized", status.sdkInitialized);
      data.put("sessionOpened", status.sessionOpened);
      
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", data);
      response.put("message", null);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("[camera-status] Failed to get camera status: {}", e.getMessage(), e);
      Map<String, Object> data = new HashMap<>();
      data.put("connected", false);
      data.put("cameraModel", getCameraModel());
      data.put("battery", null);
      data.put("lastError", e.getMessage());
      
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", data);
      response.put("message", "Failed to get camera status: " + e.getMessage());
      return ResponseEntity.ok(response); // 返回 200，但 success=false
    }
  }

  /**
   * 获取相机预览帧
   * GET /local/camera/preview.jpg
   * 
   * @param request HTTP 请求（用于检查来源）
   * @param response HTTP 响应（用于返回图片）
   */
  @GetMapping(value = "/preview.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
  public void getPreview(HttpServletRequest request, HttpServletResponse response) {
    if (!isLocalhost(request)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return;
    }

    try {
      // 尝试从 CameraAgent 获取预览帧
      // CameraAgent 可能提供 /preview/frame 或 /preview.jpg 接口
      String cameraAgentUrl = props.getCameraAgentBaseUrl();
      if (cameraAgentUrl == null || cameraAgentUrl.isEmpty()) {
        cameraAgentUrl = "http://127.0.0.1:18080";
      }
      
      URL url = new URL(cameraAgentUrl + "/preview/frame");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      conn.setRequestMethod("GET");

      int code = conn.getResponseCode();
      
      if (code >= 200 && code < 300) {
        // 成功获取预览帧，直接转发给客户端
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        
        try (InputStream is = conn.getInputStream();
             OutputStream os = response.getOutputStream()) {
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
          }
        }
        log.debug("[camera-preview] Preview frame forwarded from CameraAgent");
      } else {
        // CameraAgent 不支持预览帧接口，返回 404
        log.warn("[camera-preview] CameraAgent preview/frame not available: http={}", code);
        response.setStatus(HttpStatus.NOT_FOUND.value());
      }
    } catch (Exception e) {
      log.error("[camera-preview] Failed to get preview frame: {}", e.getMessage(), e);
      response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
  }

  /**
   * 测试拍照
   * POST /local/camera/test-shot
   * 
   * @param request HTTP 请求（用于检查来源）
   * @return 保存的文件路径
   */
  @PostMapping("/test-shot")
  public ResponseEntity<?> testShot(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      // 1. 检查相机状态
      CameraStatus status = cameraService.getStatus();
      if (!status.ok || !status.cameraConnected) {
        String error = status.error != null ? status.error : "Camera not connected";
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", null);
        response.put("message", "Camera not ready: " + error);
        return ResponseEntity.ok(response);
      }

      // 2. 创建测试目录
      Path testDir = Path.of("test");
      if (!Files.exists(testDir)) {
        Files.createDirectories(testDir);
      }

      // 3. 生成文件名（带时间戳）
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      String filename = "test_" + timestamp + ".jpg";
      Path targetFile = testDir.resolve(filename);

      // 4. 拍照
      cameraService.captureTo(targetFile);

      // 5. 验证文件是否存在
      if (!Files.exists(targetFile)) {
        throw new RuntimeException("Test shot file was not created: " + targetFile.toAbsolutePath());
      }

      // 6. 返回成功响应
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", Map.of("path", targetFile.toAbsolutePath().toString()));
      response.put("message", null);
      log.info("[camera-test-shot] Test shot saved: {}", targetFile.toAbsolutePath());
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("[camera-test-shot] Failed to capture test shot: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to capture test shot: " + e.getMessage());
      return ResponseEntity.ok(response); // 返回 200，但 success=false
    }
  }

  /**
   * 应用相机参数（部分更新）
   * POST /local/camera/apply-params
   * 
   * @param params 要应用的参数（可部分更新）
   * @param request HTTP 请求（用于检查来源）
   * @return 应用结果
   */
  @PostMapping("/apply-params")
  public ResponseEntity<?> applyParams(
      @RequestBody CameraConfig.CameraParams params,
      HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      log.info("[camera-apply-params] Applying params: iso={}, wb={}, ev={}, style={}", 
          params.getIso(), params.getWhiteBalance(), params.getExposureCompensationEv(), 
          params.getPictureStyle());

      // 1. 转换为 EDSDK 属性
      Map<String, Integer> edsdkProps = paramsConverter.convertToEdsdkProps(params);
      if (edsdkProps.isEmpty()) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", null);
        response.put("message", "No supported parameters to apply");
        return ResponseEntity.ok(response);
      }

      // 2. 应用参数到相机
      Map<String, String> failedFields = new HashMap<>();
      for (Map.Entry<String, Integer> entry : edsdkProps.entrySet()) {
        String key = entry.getKey();
        Integer value = entry.getValue();
        try {
          log.debug("[camera-apply-params] Applying property: {} = {}", key, value);
          cameraService.setProperty(key, value, false); // 先不持久化，等全部成功后再持久化
        } catch (Exception e) {
          log.warn("[camera-apply-params] Failed to apply property {} = {}: {}", 
              key, value, e.getMessage());
          failedFields.put(key, e.getMessage());
        }
      }

      // 3. 如果全部成功，写回 camera.json
      if (failedFields.isEmpty()) {
        Path file = Path.of(props.getCameraConfigFile());
        CameraConfig config = store.load(file);
        
        // 合并参数（只更新提供的字段）
        CameraConfig.CameraParams currentParams = config.getParams();
        if (currentParams == null) {
          currentParams = new CameraConfig.CameraParams();
          config.setParams(currentParams);
        }
        
        if (params.getIso() != null) currentParams.setIso(params.getIso());
        if (params.getWhiteBalance() != null) currentParams.setWhiteBalance(params.getWhiteBalance());
        if (params.getExposureCompensationEv() != null) currentParams.setExposureCompensationEv(params.getExposureCompensationEv());
        if (params.getPictureStyle() != null) currentParams.setPictureStyle(params.getPictureStyle());
        if (params.getAperture() != null) currentParams.setAperture(params.getAperture());
        if (params.getShutterSpeed() != null) currentParams.setShutterSpeed(params.getShutterSpeed());
        if (params.getMeteringMode() != null) currentParams.setMeteringMode(params.getMeteringMode());
        
        // 手动应用参数时，如果之前有预设，保存到 basePresetId，然后设置 activePresetId = "preset_custom"
        String previousPresetId = config.getActivePresetId();
        if (previousPresetId != null && !previousPresetId.equals("preset_custom")) {
          config.setBasePresetId(previousPresetId);
        }
        config.setActivePresetId("preset_custom");
        
        store.save(file, config);
        log.info("[camera-apply-params] Params applied successfully, activePresetId set to preset_custom, basePresetId={}", config.getBasePresetId());
      }

      // 4. 返回结果
      Map<String, Object> response = new HashMap<>();
      if (failedFields.isEmpty()) {
        Map<String, Object> data = new HashMap<>();
        data.put("applied", true);
        response.put("success", true);
        response.put("data", data);
        response.put("message", null);
      } else {
        response.put("success", false);
        Map<String, Object> data = new HashMap<>();
        data.put("applied", false);
        data.put("failedFields", failedFields);
        // 返回第一个失败的字段作为 failedField（向后兼容）
        String firstFailedField = failedFields.keySet().iterator().next();
        data.put("failedField", firstFailedField);
        data.put("reason", failedFields.get(firstFailedField));
        response.put("data", data);
        response.put("message", "Some parameters failed to apply");
      }
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("[camera-apply-params] Failed to apply params: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to apply params: " + e.getMessage());
      return ResponseEntity.ok(response);
    }
  }

  /**
   * 应用预设套餐
   * POST /local/camera/presets/apply
   * 
   * @param requestBody 请求体，包含 presetId
   * @param request HTTP 请求（用于检查来源）
   * @return 应用结果
   */
  @PostMapping("/presets/apply")
  public ResponseEntity<?> applyPreset(
      @RequestBody Map<String, String> requestBody,
      HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      String presetId = requestBody.get("presetId");
      if (presetId == null || presetId.isBlank()) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", null);
        response.put("message", "presetId is required");
        return ResponseEntity.ok(response);
      }

      log.info("[camera-apply-preset] Applying preset: presetId={}", presetId);

      // 1. 加载配置
      Path file = Path.of(props.getCameraConfigFile());
      CameraConfig config = store.load(file);

      // 2. 查找预设
      CameraConfig.CameraPreset preset = config.getPresets().stream()
          .filter(p -> p.getId().equals(presetId))
          .findFirst()
          .orElse(null);

      if (preset == null) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", null);
        response.put("message", "Preset not found: " + presetId);
        return ResponseEntity.ok(response);
      }

      log.info("[camera-apply-preset] Found preset: name={}, legacyProfileId={}", 
          preset.getName(), preset.getLegacyProfileId());

      // 3. 判断是否使用 legacyProfileId 分支
      boolean useLegacyProfile = preset.getLegacyProfileId() != null && !preset.getLegacyProfileId().isBlank();
      
      if (useLegacyProfile) {
        log.info("[camera-apply-preset] Using legacy profile branch: legacyProfileId={}", preset.getLegacyProfileId());
        
        // 调用旧的 CameraProfileService.apply()（service 内部调用，不走 HTTP）
        try {
          CameraProfileService.ApplyProfileResult result = profileService.applyProfile(
              preset.getLegacyProfileId(), false);  // persist=false，因为我们会更新 camera.json
          
          log.info("[camera-apply-preset] Legacy profile apply result: success={}, failedProps={}", 
              result.isSuccess(), result.getFailedProps());
          
          // 4. 如果全部成功，更新 camera.json
          if (result.isSuccess()) {
            // 更新 activePresetId
            config.setActivePresetId(presetId);
            // 清除 basePresetId（因为重新应用预设）
            config.setBasePresetId(null);
            
            // 更新 params：使用 preset 的 params（占位参数）或保留现有 params
            if (preset.getParams() != null) {
              config.setParams(new CameraConfig.CameraParams(preset.getParams()));
            }
            // 如果 preset 的 params 为空，保留现有 params 不变
            
            store.save(file, config);
            log.info("[camera-apply-preset] Legacy profile applied successfully, camera.json updated: activePresetId={}", presetId);
          }
          
          // 5. 返回结果
          Map<String, Object> response = new HashMap<>();
          if (result.isSuccess()) {
            Map<String, Object> data = new HashMap<>();
            data.put("applied", true);
            response.put("success", true);
            response.put("data", data);
            response.put("message", null);
          } else {
            response.put("success", false);
            Map<String, Object> data = new HashMap<>();
            data.put("applied", false);
            data.put("failedFields", result.getFailedProps());
            if (!result.getFailedProps().isEmpty()) {
              String firstFailedField = result.getFailedProps().keySet().iterator().next();
              data.put("failedField", firstFailedField);
              data.put("reason", result.getFailedProps().get(firstFailedField));
            }
            response.put("data", data);
            response.put("message", "Some parameters failed to apply");
          }
          return ResponseEntity.ok(response);
          
        } catch (IllegalArgumentException e) {
          log.error("[camera-apply-preset] Legacy profile not found: {}", preset.getLegacyProfileId());
          Map<String, Object> response = new HashMap<>();
          response.put("success", false);
          response.put("data", null);
          response.put("message", "Legacy profile not found: " + preset.getLegacyProfileId());
          return ResponseEntity.ok(response);
        } catch (Exception e) {
          log.error("[camera-apply-preset] Failed to apply legacy profile: {}", e.getMessage(), e);
          Map<String, Object> response = new HashMap<>();
          response.put("success", false);
          response.put("data", null);
          response.put("message", "Failed to apply legacy profile: " + e.getMessage());
          return ResponseEntity.ok(response);
        }
      } else {
        // 原有的新 preset 逻辑（使用 CameraParamsConverter）
        log.info("[camera-apply-preset] Using new preset branch: converting params to EDSDK");
        
        // 3. 应用预设参数
        Map<String, Integer> edsdkProps = paramsConverter.convertToEdsdkProps(preset.getParams());
        if (edsdkProps.isEmpty()) {
          Map<String, Object> response = new HashMap<>();
          response.put("success", false);
          response.put("data", null);
          response.put("message", "No supported parameters in preset");
          return ResponseEntity.ok(response);
        }

        Map<String, String> failedFields = new HashMap<>();
        for (Map.Entry<String, Integer> entry : edsdkProps.entrySet()) {
          String key = entry.getKey();
          Integer value = entry.getValue();
          try {
            log.debug("[camera-apply-preset] Applying property: {} = {}", key, value);
            cameraService.setProperty(key, value, false);
          } catch (Exception e) {
            log.warn("[camera-apply-preset] Failed to apply property {} = {}: {}", 
                key, value, e.getMessage());
            failedFields.put(key, e.getMessage());
          }
        }

        // 4. 如果全部成功，更新 camera.json
        if (failedFields.isEmpty()) {
          // 更新 params
          config.setParams(new CameraConfig.CameraParams(preset.getParams()));
          // 更新 activePresetId
          config.setActivePresetId(presetId);
          // 清除 basePresetId（因为重新应用预设）
          config.setBasePresetId(null);
          
          store.save(file, config);
          log.info("[camera-apply-preset] Preset applied successfully and saved to camera.json: activePresetId={}", presetId);
        }

        // 5. 返回结果
        Map<String, Object> response = new HashMap<>();
        if (failedFields.isEmpty()) {
          Map<String, Object> data = new HashMap<>();
          data.put("applied", true);
          response.put("success", true);
          response.put("data", data);
          response.put("message", null);
        } else {
          response.put("success", false);
          Map<String, Object> data = new HashMap<>();
          data.put("applied", false);
          data.put("failedFields", failedFields);
          String firstFailedField = failedFields.keySet().iterator().next();
          data.put("failedField", firstFailedField);
          data.put("reason", failedFields.get(firstFailedField));
          response.put("data", data);
          response.put("message", "Some parameters failed to apply");
        }
        return ResponseEntity.ok(response);
      }

    } catch (Exception e) {
      log.error("[camera-apply-preset] Failed to apply preset: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to apply preset: " + e.getMessage());
      return ResponseEntity.ok(response);
    }
  }

  /**
   * 更新预设的参数
   * PUT /local/camera/presets/{presetId}/params
   * 
   * @param presetId 预设 ID
   * @param params 新的参数值
   * @param request HTTP 请求（用于检查来源）
   * @return 更新结果
   */
  @PutMapping("/presets/{presetId}/params")
  public ResponseEntity<?> updatePresetParams(
      @PathVariable String presetId,
      @RequestBody CameraConfig.CameraParams params,
      HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      log.info("[camera-update-preset-params] Updating preset params: presetId={}, iso={}, wb={}, ev={}, style={}", 
          presetId, params.getIso(), params.getWhiteBalance(), params.getExposureCompensationEv(), 
          params.getPictureStyle());

      // 1. 加载配置
      Path file = Path.of(props.getCameraConfigFile());
      CameraConfig config = store.load(file);

      // 2. 查找预设
      CameraConfig.CameraPreset preset = config.getPresets().stream()
          .filter(p -> p.getId().equals(presetId))
          .findFirst()
          .orElse(null);

      if (preset == null) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", null);
        response.put("message", "Preset not found: " + presetId);
        return ResponseEntity.ok(response);
      }

      // 3. 检查是否是 legacy preset（不允许修改 legacy preset 的参数）
      if (preset.getLegacyProfileId() != null && !preset.getLegacyProfileId().isBlank()) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", null);
        response.put("message", "Cannot update params for legacy preset (preset uses legacyProfileId). PresetId: " + presetId);
        return ResponseEntity.ok(response);
      }

      // 4. 更新预设的参数（只更新提供的字段）
      CameraConfig.CameraParams presetParams = preset.getParams();
      if (presetParams == null) {
        presetParams = new CameraConfig.CameraParams();
        preset.setParams(presetParams);
      }

      if (params.getIso() != null) presetParams.setIso(params.getIso());
      if (params.getWhiteBalance() != null) presetParams.setWhiteBalance(params.getWhiteBalance());
      if (params.getExposureCompensationEv() != null) presetParams.setExposureCompensationEv(params.getExposureCompensationEv());
      if (params.getPictureStyle() != null) presetParams.setPictureStyle(params.getPictureStyle());
      if (params.getAperture() != null) presetParams.setAperture(params.getAperture());
      if (params.getShutterSpeed() != null) presetParams.setShutterSpeed(params.getShutterSpeed());
      if (params.getMeteringMode() != null) presetParams.setMeteringMode(params.getMeteringMode());

      // 5. 保存到文件
      store.save(file, config);
      log.info("[camera-update-preset-params] Preset params updated successfully: presetId={}", presetId);

      // 6. 返回结果
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      Map<String, Object> data = new HashMap<>();
      data.put("presetId", presetId);
      data.put("updatedParams", presetParams);
      response.put("data", data);
      response.put("message", null);
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("[camera-update-preset-params] Failed to update preset params: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to update preset params: " + e.getMessage());
      return ResponseEntity.ok(response);
    }
  }

  /**
   * 获取所有预设列表
   * GET /local/camera/presets
   * 
   * @param request HTTP 请求（用于检查来源）
   * @return 预设列表
   */
  @GetMapping("/presets")
  public ResponseEntity<?> listPresets(HttpServletRequest request) {
    if (!isLocalhost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse("Access denied: only localhost allowed"));
    }

    try {
      Path file = Path.of(props.getCameraConfigFile());
      CameraConfig config = store.load(file);
      
      List<Map<String, Object>> presetList = new ArrayList<>();
      for (CameraConfig.CameraPreset preset : config.getPresets()) {
        Map<String, Object> presetInfo = new HashMap<>();
        presetInfo.put("id", preset.getId());
        presetInfo.put("name", preset.getName());
        presetInfo.put("displayName", preset.getDisplayName() != null ? preset.getDisplayName() : preset.getName());
        presetInfo.put("category", preset.getCategory() != null ? preset.getCategory() : "ENV");
        presetInfo.put("tags", preset.getTags());
        presetInfo.put("legacyProfileId", preset.getLegacyProfileId());
        
        // 添加参数预览（如果有）
        if (preset.getParams() != null) {
          Map<String, Object> paramsPreview = new HashMap<>();
          CameraConfig.CameraParams params = preset.getParams();
          if (params.getIso() != null) paramsPreview.put("iso", params.getIso());
          if (params.getWhiteBalance() != null) paramsPreview.put("whiteBalance", params.getWhiteBalance());
          if (params.getExposureCompensationEv() != null) paramsPreview.put("exposureCompensationEv", params.getExposureCompensationEv());
          if (params.getPictureStyle() != null) paramsPreview.put("pictureStyle", params.getPictureStyle());
          if (params.getAperture() != null) paramsPreview.put("aperture", params.getAperture());
          if (params.getShutterSpeed() != null) paramsPreview.put("shutterSpeed", params.getShutterSpeed());
          if (params.getMeteringMode() != null) paramsPreview.put("meteringMode", params.getMeteringMode());
          presetInfo.put("paramsPreview", paramsPreview);
        }
        
        presetList.add(presetInfo);
      }
      
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", presetList);
      response.put("message", null);
      return ResponseEntity.ok(response);
      
    } catch (Exception e) {
      log.error("[camera-list-presets] Failed to list presets: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("data", null);
      response.put("message", "Failed to list presets: " + e.getMessage());
      return ResponseEntity.ok(response);
    }
  }

  /**
   * 获取相机型号（从配置中读取）
   */
  private String getCameraModel() {
    try {
      Path file = Path.of(props.getCameraConfigFile());
      CameraConfig config = store.load(file);
      return config.getCameraModel() != null ? config.getCameraModel() : "Unknown";
    } catch (Exception e) {
      return "Unknown";
    }
  }

  /**
   * 检查请求是否来自 localhost
   * 
   * @param request HTTP 请求
   * @return true 如果是 localhost，false 否则
   */
  private boolean isLocalhost(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    String host = request.getHeader("Host");
    
    // 检查 IP 地址
    if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
      return true;
    }
    
    // 检查 Host 头（处理反向代理情况）
    if (host != null && (host.startsWith("localhost:") || host.startsWith("127.0.0.1:"))) {
      return true;
    }
    
    // 检查 X-Forwarded-For 头（处理反向代理）
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null) {
      String firstIp = forwardedFor.split(",")[0].trim();
      if ("127.0.0.1".equals(firstIp) || "localhost".equals(firstIp)) {
        return true;
      }
    }
    
    log.warn("[camera-config] Access denied from non-localhost: remoteAddr={}, host={}", remoteAddr, host);
    return false;
  }

  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("message", message);
    return response;
  }
}
