package com.mg.booth.service;

import com.mg.booth.camera.CameraService;
import com.mg.booth.client.AiGatewayClient;
import com.mg.booth.client.dto.AiProcessRequest;
import com.mg.booth.client.dto.AiProcessResponse;
import com.mg.booth.config.BoothProps;
import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionProgress;
import com.mg.booth.domain.SessionState;
import com.mg.booth.dto.ApiError;
import com.mg.booth.dto.CaptureRequest;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.SelectTemplateRequest;
import com.mg.booth.exception.ConflictException;
import com.mg.booth.exception.NotFoundException;
import com.mg.booth.util.RawPathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class SessionService {

  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

  private final Map<String, Session> store = new ConcurrentHashMap<>();
  private final TemplateService templateService;
  private final SessionStateMachine sm;
  private final StorageService storageService;
  private final CameraService cameraService;
  private final MockAiService mockAiService;
  private final Executor boothExecutor;
  private final DeliveryService deliveryService;
  private final AiGatewayClient aiGatewayClient;
  private final BoothProps boothProps;
  private final AiProcessV2Service aiProcessV2Service;
  private final com.mg.booth.config.AppProps appProps;

  public SessionService(
    TemplateService templateService,
    SessionStateMachine sm,
    StorageService storageService,
    @Qualifier("cameraAgentCameraService")CameraService cameraService,
    MockAiService mockAiService,
    @Qualifier("boothExecutor") Executor boothExecutor,
    DeliveryService deliveryService,
    AiGatewayClient aiGatewayClient,
    BoothProps boothProps,
    AiProcessV2Service aiProcessV2Service,
    com.mg.booth.config.AppProps appProps
  ) {
    this.templateService = templateService;
    this.sm = sm;
    this.storageService = storageService;
    this.cameraService = cameraService;
    this.mockAiService = mockAiService;
    this.boothExecutor = boothExecutor;
    this.deliveryService = deliveryService;
    this.aiGatewayClient = aiGatewayClient;
    this.boothProps = boothProps;
    this.aiProcessV2Service = aiProcessV2Service;
    this.appProps = appProps;
  }

  //类似会话状态机的切换按钮
  private void enterState(Session s, SessionState to, SessionProgress progress) {
    s.setState(to);
    s.setProgress(progress);
    s.setUpdatedAt(OffsetDateTime.now());
    s.setStateEnteredAt(s.getUpdatedAt());
  }

  public Map<String, Session> unsafeStore() {
    // 给 Sweeper 用（MVP 简化）
    return store;
  }

  public Session create(CreateSessionRequest req) {
    String id = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    OffsetDateTime now = OffsetDateTime.now();

    if (!sm.canTransition(SessionState.IDLE, SessionState.SELECTING)) {
      throw new ConflictException("INVALID_STATE", "Cannot create session");
    }

    Session s = new Session();
    s.setSessionId(id);
    s.setTemplateId(null);
    s.setAttemptIndex(0);
    s.setMaxRetries(req.getMaxRetries());
    s.setRetriesLeft(req.getMaxRetries());
    s.setCountdownSeconds(req.getCountdownSeconds());

    s.setRawUrl(null);
    s.setPreviewUrl(null);
    s.setFinalUrl(null);

    // Phase 4: 初始化相机预览 URL（下发给前端）
    String cameraBaseUrl = boothProps.getCameraAgentBaseUrl();
    if (cameraBaseUrl != null && !cameraBaseUrl.isEmpty()) {
      s.setCameraPreviewUrl(cameraBaseUrl + "/preview");
      s.setCameraStatusUrl(cameraBaseUrl + "/status");
    }

    s.setCaptureJobRunning(false);
    s.setAiJobRunning(false);
    s.setError(null);

    s.setCreatedAt(now);
    s.setUpdatedAt(now);
    s.setStateEnteredAt(now);

    enterState(s, SessionState.SELECTING, new SessionProgress(SessionProgress.Step.NONE, "等待选择模板", 0));
    store.put(id, s);
    return s;
  }

  public Session get(String sessionId) {
    Session s = store.get(sessionId);
    if (s == null) throw new NotFoundException("Session not found: " + sessionId);
    return s;
  }

  public Session selectTemplate(String sessionId, SelectTemplateRequest req) {
    Session s = get(sessionId);

    boolean exists = templateService.listTemplates().stream()
      .anyMatch(t -> t.isEnabled() && t.getTemplateId().equals(req.getTemplateId()));
    if (!exists) {
      throw new NotFoundException("Template not found or disabled: " + req.getTemplateId());
    }

    // Phase 4: 策略2一起改，选完模板直接进入 LIVE_PREVIEW（取景）
    if (!sm.canTransition(s.getState(), SessionState.LIVE_PREVIEW)) {
      throw new ConflictException("INVALID_STATE", "Action not allowed in current state: " + s.getState());
    }

    s.setTemplateId(req.getTemplateId());
    s.setError(null);
    enterState(s, SessionState.LIVE_PREVIEW, new SessionProgress(SessionProgress.Step.NONE, "请取景，准备拍照", 0));
    System.out.println("[SessionService] selectTemplate: sessionId=" + sessionId + ", newState=" + s.getState() + ", cameraPreviewUrl=" + s.getCameraPreviewUrl());
    return s;
  }

  /**
   * Phase 4: 从 LIVE_PREVIEW 进入 COUNTDOWN（用户点击"开始拍照"）
   */
  public Session enterCountdown(String sessionId) {
    Session s = get(sessionId);

    // 幂等：已经是 COUNTDOWN 就直接返回
    if (s.getState() == SessionState.COUNTDOWN) return s;

    if (!sm.canTransition(s.getState(), SessionState.COUNTDOWN)) {
      throw new ConflictException("INVALID_STATE", "Action not allowed in current state: " + s.getState());
    }

    enterState(s, SessionState.COUNTDOWN, new SessionProgress(SessionProgress.Step.NONE, "准备倒计时", 0));
    return s;
  }

  /**
   * Day3+4: capture + chain mock AI to reach PREVIEW
   */
  public Session capture(String sessionId, CaptureRequest req) {
    Session s = get(sessionId);

    Integer clientAttempt = (req == null) ? null : req.getAttemptIndex();
    if (clientAttempt != null && !clientAttempt.equals(s.getAttemptIndex())) {
      throw new ConflictException(
        "INVALID_STATE",
        "attemptIndex mismatch. current=" + s.getAttemptIndex() + ", request=" + clientAttempt
      );
    }

    // idempotency: if already in capturing/processing/preview, just return
    if (s.getState() == SessionState.CAPTURING
      || s.getState() == SessionState.PROCESSING
      || s.getState() == SessionState.PREVIEW) {
      return s;
    }

    if (!sm.canTransition(s.getState(), SessionState.CAPTURING)) {
      throw new ConflictException("INVALID_STATE", "Action not allowed in current state: " + s.getState());
    }

    synchronized (s) {
      if (s.isCaptureJobRunning()) return s;
      s.setCaptureJobRunning(true);
      s.setError(null);
      enterState(s, SessionState.CAPTURING, new SessionProgress(SessionProgress.Step.NONE, "拍照中…", 5));
    }

    int attemptIndex = s.getAttemptIndex();

    // ✅ 改：按 sess_{sessionId}/IMG_{timestamp}.jpg 生成
    Path rawPath;
    try {
      rawPath = RawPathUtil.buildTargetFile(boothProps.getSharedRawBaseDir(), sessionId);
    } catch (Exception e) {
      throw new RuntimeException("Failed to build raw target file", e);
    }

    storageService.ensureDir(rawPath.getParent());

    boothExecutor.execute(() -> {
      try {
        // 1) 拍照到共享目录
        cameraService.captureTo(rawPath);

        synchronized (s) {
          // 这里直接保存实际 rawPath，MVP 阶段前端只要能展示就行
          s.setRawUrl(rawPath.toString());
          s.setCaptureJobRunning(false);

          // enter PROCESSING
          if (!sm.canTransition(s.getState(), SessionState.PROCESSING)) {
            throw new RuntimeException("Invalid transition to PROCESSING from " + s.getState());
          }
          enterState(s, SessionState.PROCESSING,
            new SessionProgress(SessionProgress.Step.CAPTURE_DONE, "拍照完成，准备AI", 20));
        }

        // 2) Start AI job (防重复)
        synchronized (s) {
          if (s.isAiJobRunning()) return;
          s.setAiJobRunning(true);
          s.setProgress(new SessionProgress(SessionProgress.Step.AI_QUEUED, "AI排队中…", 30));
          s.setUpdatedAt(OffsetDateTime.now());
        }

        // 3) 按 mode 分发 AI 处理
        String aiMode = (appProps != null && appProps.getAi() != null && appProps.getAi().getMode() != null)
            ? appProps.getAi().getMode()
            : "v2";
        
        if ("v2".equalsIgnoreCase(aiMode)) {
          // V2: 模板驱动逻辑
          processAiV2(s, rawPath, attemptIndex);
        } else {
          // V1: 原有逻辑（向后兼容）
          processAiV1(s, rawPath, attemptIndex);
          // 4) Enter PREVIEW（仅 v1 逻辑保留原有 PREVIEW 状态）
          synchronized (s) {
            if (!sm.canTransition(s.getState(), SessionState.PREVIEW)) {
              throw new RuntimeException("Invalid transition to PREVIEW from " + s.getState());
            }
            s.setAiJobRunning(false);
            enterState(s, SessionState.PREVIEW, new SessionProgress(SessionProgress.Step.FINAL_READY, "请确认 / 重拍", 100));
          }
        }

      } catch (Exception e) {
        synchronized (s) {
          s.setCaptureJobRunning(false);
          s.setAiJobRunning(false);
          // 确保 error.detail.reason 包含真实异常原因
          String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
          if (e.getCause() != null && e.getCause().getMessage() != null) {
            reason = reason + " (cause: " + e.getCause().getMessage() + ")";
          }
          s.setError(new ApiError("PROCESSING_FAILED", "Capture/AI failed", Map.of("reason", reason)));
          enterState(s, SessionState.ERROR, new SessionProgress(SessionProgress.Step.NONE, "处理失败，返回首页", 0));
        }
      }
    });

    return s;
  }

  /**
   * V1 AI processing logic (legacy)
   * 原有的 AI Gateway 调用逻辑
   */
  private void processAiV1(Session s, Path rawPath, int attemptIndex) {
    String sessionId = s.getSessionId();
    
    // 组装 gateway 请求（带 FULL template）
    var tpl = templateService.listTemplates().stream()
      .filter(t -> t.isEnabled() && t.getTemplateId().equals(s.getTemplateId()))
      .findFirst()
      .orElseThrow(() -> new RuntimeException("Template not found: " + s.getTemplateId()));

    // 当前 MVP 的 TemplateSummary 只有 id/name/enabled，这里先只传最小字段。
    // 后续你扩展 TemplateSummary 字段时，可以这在里补齐 pipeline 需要的字段。

    AiProcessRequest areq = new AiProcessRequest();
    areq.setSessionId(sessionId);
    areq.setAttemptIndex(attemptIndex);
    areq.setTemplateId(tpl.getTemplateId());
    areq.setRawPath(rawPath.toString());

    // options/output（MVP：先给默认值；以后可从前端传）
    areq.setOptions(Map.of(
            "bgMode", "STATIC",
            "segmentation", "AUTO",
            "featherPx", 6,
            "strength", 0.6
    ));
    areq.setOutput(Map.of(
            "previewWidth", 900,
            "finalWidth", 1800
    ));

    String idemKey = sessionId + "#" + attemptIndex + "#" + tpl.getTemplateId();

    // 调用 AI Gateway
    synchronized (s) {
      s.setProgress(new SessionProgress(SessionProgress.Step.AI_PROCESSING, "AI处理中…", 60));
      s.setUpdatedAt(OffsetDateTime.now());
    }

    // deviceId 建议从 boothProps 注入（或写死 kiosk-001）
    String deviceId = boothProps.getDeviceId();

    AiProcessResponse aresp = aiGatewayClient.process(deviceId, idemKey, areq);

    if (aresp == null || !aresp.isOk()) {
      String reason = (aresp == null || aresp.getError() == null)
        ? "gateway_failed"
        : (aresp.getError().getCode() + ":" + aresp.getError().getMessage());
      throw new RuntimeException("AI Gateway failed: " + reason);
    }

    synchronized (s) {
      s.setPreviewUrl(aresp.getPreviewUrl());
      s.setFinalUrl(aresp.getFinalUrl());
      s.setProgress(new SessionProgress(SessionProgress.Step.FINAL_READY, "生成成品…", 95));
      s.setUpdatedAt(OffsetDateTime.now());
    }
  }

  /**
   * 构建公网可访问的完整 URL
   * @param path 相对路径（如 "/d/tok_xxx"）
   * @return 完整的公网 URL
   */
  private String buildPublicUrl(String path) {
    String base = boothProps.getPublicBaseUrl();
    if (base == null || base.isBlank()) {
      log.warn("[session-service] booth.publicBaseUrl is empty; using localhost fallback for dev");
      base = "http://localhost:8080";
    }
    // 处理 base 尾部 /
    return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
  }

  /**
   * V2 AI processing logic (template-driven)
   * 新的模板驱动处理逻辑
   */
  private void processAiV2(Session s, Path rawPath, int attemptIndex) {
    String sessionId = s.getSessionId();
    
    synchronized (s) {
      s.setProgress(new SessionProgress(SessionProgress.Step.AI_PROCESSING, "AI处理中…", 60));
      s.setUpdatedAt(OffsetDateTime.now());
    }

    // 调用 V2 服务
    aiProcessV2Service.process(s);

    synchronized (s) {
      // 标记 AI 任务已完成
      s.setAiJobRunning(false);
      // 如果处理成功且生成了 finalUrl，自动生成 downloadUrl 并进入 DELIVERING（v2 自动完成流程）
      if (s.getFinalUrl() != null && s.getError() == null) {
        // 自动生成下载 token 和 URL（与 confirm() 逻辑一致）
        try {
          var rec = deliveryService.createToken(sessionId, 120);
          String token = rec.getToken();
          s.setDownloadToken(token);
          s.setDownloadUrl(buildPublicUrl("/d/" + token));
          
          // 进入 DELIVERING 状态（而不是 DONE），这样前端可以显示二维码
          if (sm.canTransition(s.getState(), SessionState.DELIVERING)) {
            enterState(s, SessionState.DELIVERING,
              new SessionProgress(SessionProgress.Step.DELIVERY_READY, "扫码下载照片", 100));
            
            log.info("[session-service] V2 processing success: sessionId={}, state={}, finalUrl={}, downloadUrl={}",
                sessionId, s.getState(), s.getFinalUrl(), s.getDownloadUrl());
          } else if (sm.canTransition(s.getState(), SessionState.DONE)) {
            // 如果无法进入 DELIVERING，则进入 DONE（兜底）
            enterState(s, SessionState.DONE,
              new SessionProgress(SessionProgress.Step.FINAL_READY, "生成成品…", 100));
            
            log.warn("[session-service] V2 processing success but cannot transition to DELIVERING: sessionId={}, state={}, finalUrl={}, downloadUrl={}",
                sessionId, s.getState(), s.getFinalUrl(), s.getDownloadUrl());
          }
        } catch (Exception e) {
          log.error("[session-service] Failed to create delivery token for v2: sessionId={}, error={}",
              sessionId, e.getMessage(), e);
          // 如果生成 token 失败，仍然进入 DONE 状态（至少可以显示最终图片）
          if (sm.canTransition(s.getState(), SessionState.DONE)) {
            enterState(s, SessionState.DONE,
              new SessionProgress(SessionProgress.Step.FINAL_READY, "生成成品…", 100));
          }
        }
      }
    }
  }

  public void finish(String sessionId, String reason) {
    Session s = get(sessionId);

    if (!sm.canTransition(s.getState(), SessionState.IDLE)) {
      throw new ConflictException("INVALID_STATE", "Finish not allowed in current state: " + s.getState());
    }

    s.setTemplateId(null);
    s.setRawUrl(null);
    s.setPreviewUrl(null);
    s.setFinalUrl(null);
    s.setDownloadToken(null);
    s.setDownloadUrl(null);
    s.setError(null);
    s.setCaptureJobRunning(false);
    s.setAiJobRunning(false);

    enterState(s, SessionState.IDLE, new SessionProgress(SessionProgress.Step.NONE, "已回到首页", 0));
  }

  public Session retry(String sessionId, String reason) {
    Session s = get(sessionId);

    if (!sm.canTransition(s.getState(), SessionState.COUNTDOWN)) {
      throw new ConflictException("INVALID_STATE", "Retry not allowed in current state: " + s.getState());
    }

    if (s.getRetriesLeft() == null || s.getRetriesLeft() <= 0) {
      throw new ConflictException("NO_RETRIES_LEFT", "No retries left");
    }

    // 关键：attemptIndex++，retriesLeft--
    synchronized (s) {
      s.setRetriesLeft(s.getRetriesLeft() - 1);
      s.setAttemptIndex(s.getAttemptIndex() + 1);

      // 清理上一次生成的图和交付信息（避免前端误拿旧图）
      s.setRawUrl(null);
      s.setPreviewUrl(null);
      s.setFinalUrl(null);
      s.setDownloadToken(null);
      s.setDownloadUrl(null);
      s.setError(null);

      s.setCaptureJobRunning(false);
      s.setAiJobRunning(false);

      enterState(s, SessionState.COUNTDOWN, new SessionProgress(SessionProgress.Step.NONE, "准备重拍倒计时", 0));
    }
    return s;
  }

  public Session confirm(String sessionId) {
    Session s = get(sessionId);

    if (s.getState() == SessionState.DELIVERING || s.getState() == SessionState.DONE) {
      // 幂等：已经生成过 token 直接返回
      log.debug("[session-service] Confirm called but already in DELIVERING/DONE: sessionId={}, state={}",
          sessionId, s.getState());
      return s;
    }

    if (!sm.canTransition(s.getState(), SessionState.DELIVERING)) {
      throw new ConflictException("INVALID_STATE", "Confirm not allowed in current state: " + s.getState());
    }

    if (s.getFinalUrl() == null) {
      throw new ConflictException("INVALID_STATE", "Final image not ready");
    }

    synchronized (s) {
      // 生成 token（TTL 120s，够演示）
      var rec = deliveryService.createToken(sessionId, 120);
      String token = rec.getToken();
      s.setDownloadToken(token);
      s.setDownloadUrl(buildPublicUrl("/d/" + token));

      enterState(s, SessionState.DELIVERING,
        new SessionProgress(SessionProgress.Step.DELIVERY_READY, "扫码下载照片", 100));
      
      log.info("[session-service] V1 confirm success: sessionId={}, state={}, finalUrl={}, downloadUrl={}",
          sessionId, s.getState(), s.getFinalUrl(), s.getDownloadUrl());
    }

    return s;
  }
}

