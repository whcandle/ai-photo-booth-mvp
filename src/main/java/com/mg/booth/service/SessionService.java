package com.mg.booth.service;

import com.mg.booth.camera.CameraService;
import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionProgress;
import com.mg.booth.domain.SessionState;
import com.mg.booth.dto.ApiError;
import com.mg.booth.dto.CaptureRequest;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.SelectTemplateRequest;
import com.mg.booth.exception.ConflictException;
import com.mg.booth.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class SessionService {

  private final Map<String, Session> store = new ConcurrentHashMap<>();
  private final TemplateService templateService;
  private final SessionStateMachine sm;
  private final StorageService storageService;
  private final CameraService cameraService;
  private final MockAiService mockAiService;
  private final Executor boothExecutor;
  private final DeliveryService deliveryService;

  public SessionService(
    TemplateService templateService,
    SessionStateMachine sm,
    StorageService storageService,
    @Qualifier("usbCameraService")CameraService cameraService,
    MockAiService mockAiService,
    @Qualifier("boothExecutor") Executor boothExecutor,
    DeliveryService deliveryService
  ) {
    this.templateService = templateService;
    this.sm = sm;
    this.storageService = storageService;
    this.cameraService = cameraService;
    this.mockAiService = mockAiService;
    this.boothExecutor = boothExecutor;
    this.deliveryService = deliveryService;
  }

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

    if (!sm.canTransition(s.getState(), SessionState.COUNTDOWN)) {
      throw new ConflictException("INVALID_STATE", "Action not allowed in current state: " + s.getState());
    }

    s.setTemplateId(req.getTemplateId());
    s.setError(null);
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
    var rawPath = storageService.rawFilePath(sessionId, attemptIndex);
    storageService.ensureDir(rawPath.getParent());

    boothExecutor.execute(() -> {
      try {
        // 1) Mock camera
        cameraService.captureTo(rawPath);

        synchronized (s) {
          s.setRawUrl(storageService.rawUrl(sessionId, attemptIndex));
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

        // 3) Progress: AI processing
        Thread.sleep(800);
        synchronized (s) {
          s.setProgress(new SessionProgress(SessionProgress.Step.AI_PROCESSING, "AI处理中…", 60));
          s.setUpdatedAt(OffsetDateTime.now());
        }

        // 4) Mock AI work: generate preview/final
        var previewPath = storageService.previewFilePath(sessionId, attemptIndex);
        var finalPath = storageService.finalFilePath(sessionId, attemptIndex);
        storageService.ensureDir(previewPath.getParent());
        storageService.ensureDir(finalPath.getParent());

        // 再推进一点进度
        Thread.sleep(600);
        synchronized (s) {
          s.setProgress(new SessionProgress(SessionProgress.Step.PREVIEW_READY, "生成预览…", 80));
          s.setUpdatedAt(OffsetDateTime.now());
        }

        mockAiService.process(rawPath, previewPath, finalPath);

        synchronized (s) {
          s.setPreviewUrl(storageService.previewUrl(sessionId, attemptIndex));
          s.setFinalUrl(storageService.finalUrl(sessionId, attemptIndex));
          s.setProgress(new SessionProgress(SessionProgress.Step.FINAL_READY, "生成成品…", 95));
          s.setUpdatedAt(OffsetDateTime.now());
        }

        // 5) Enter PREVIEW
        synchronized (s) {
          if (!sm.canTransition(s.getState(), SessionState.PREVIEW)) {
            throw new RuntimeException("Invalid transition to PREVIEW from " + s.getState());
          }
          s.setAiJobRunning(false);
          enterState(s, SessionState.PREVIEW, new SessionProgress(SessionProgress.Step.FINAL_READY, "请确认 / 重拍", 100));
        }

      } catch (Exception e) {
        synchronized (s) {
          s.setCaptureJobRunning(false);
          s.setAiJobRunning(false);
          s.setError(new ApiError("PROCESSING_FAILED", "Capture/AI failed", Map.of("reason", e.getMessage())));
          enterState(s, SessionState.ERROR, new SessionProgress(SessionProgress.Step.NONE, "处理失败，返回首页", 0));
        }
      }
    });

    return s;
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
      var rec = deliveryService.createToken(s.getSessionId(), 120);
      s.setDownloadToken(rec.getToken());
      s.setDownloadUrl("/d/" + rec.getToken());

      enterState(s, SessionState.DELIVERING,
        new SessionProgress(SessionProgress.Step.DELIVERY_READY, "扫码下载照片", 100));
    }

    return s;
  }
}

