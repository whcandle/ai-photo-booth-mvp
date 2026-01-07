package com.mg.booth.service;

import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionProgress;
import com.mg.booth.domain.SessionState;
import com.mg.booth.dto.CaptureRequest;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.SelectTemplateRequest;
import com.mg.booth.exception.ConflictException;
import com.mg.booth.exception.NotFoundException;
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
  private final MockCameraService mockCameraService;
  private final Executor boothExecutor;

  public SessionService(
    TemplateService templateService,
    SessionStateMachine sm,
    StorageService storageService,
    MockCameraService mockCameraService,
    Executor boothExecutor
  ) {
    this.templateService = templateService;
    this.sm = sm;
    this.storageService = storageService;
    this.mockCameraService = mockCameraService;
    this.boothExecutor = boothExecutor;
  }

  public Session create(CreateSessionRequest req) {
    String id = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    OffsetDateTime now = OffsetDateTime.now();

    if (!sm.canTransition(SessionState.IDLE, SessionState.SELECTING)) {
      throw new ConflictException("INVALID_STATE", "Cannot create session");
    }

    Session s = new Session();
    s.setSessionId(id);
    s.setState(SessionState.SELECTING);
    s.setTemplateId(null);
    s.setAttemptIndex(0);
    s.setMaxRetries(req.getMaxRetries());
    s.setRetriesLeft(req.getMaxRetries());
    s.setCountdownSeconds(req.getCountdownSeconds());
    s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "等待选择模板", 0));
    s.setRawUrl(null);
    s.setCaptureJobRunning(false);
    s.setCreatedAt(now);
    s.setUpdatedAt(now);

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
    s.setState(SessionState.COUNTDOWN);
    s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "准备倒计时", 0));
    s.setUpdatedAt(OffsetDateTime.now());
    return s;
  }

  /**
   * Day3: Trigger capture (mock camera).
   * - Only allowed in COUNTDOWN
   * - Idempotent: if already CAPTURING/PROCESSING, return current session
   * - Prevent duplicate job with captureJobRunning flag
   */
  public Session capture(String sessionId, CaptureRequest req) {
    Session s = get(sessionId);

    // Optional: client may pass attemptIndex; we ignore unless matches current
    Integer clientAttempt = (req == null) ? null : req.getAttemptIndex();
    if (clientAttempt != null && !clientAttempt.equals(s.getAttemptIndex())) {
      throw new ConflictException(
        "INVALID_STATE",
        "attemptIndex mismatch. current=" + s.getAttemptIndex() + ", request=" + clientAttempt
      );
    }

    // Idempotency by state:
    if (s.getState() == SessionState.CAPTURING || s.getState() == SessionState.PROCESSING) {
      return s;
    }

    if (!sm.canTransition(s.getState(), SessionState.CAPTURING)) {
      throw new ConflictException("INVALID_STATE", "Action not allowed in current state: " + s.getState());
    }

    // Prevent duplicate job start (e.g., double click)
    synchronized (s) {
      if (s.isCaptureJobRunning()) {
        return s; // already started
      }
      s.setCaptureJobRunning(true);
      s.setState(SessionState.CAPTURING);
      s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "拍照中…", 5));
      s.setUpdatedAt(OffsetDateTime.now());
    }

    int attemptIndex = s.getAttemptIndex();
    var rawPath = storageService.rawFilePath(sessionId, attemptIndex);
    storageService.ensureDir(rawPath.getParent());

    // async job
    boothExecutor.execute(() -> {
      try {
        mockCameraService.captureTo(rawPath);

        synchronized (s) {
          s.setRawUrl(storageService.rawUrl(sessionId, attemptIndex));
          s.setProgress(new SessionProgress(SessionProgress.Step.CAPTURE_DONE, "拍照完成", 20));
          // Day3 ends here, but we advance to PROCESSING for Day4 extension
          if (sm.canTransition(s.getState(), SessionState.PROCESSING)) {
            s.setState(SessionState.PROCESSING);
            s.setProgress(new SessionProgress(SessionProgress.Step.AI_QUEUED, "等待AI处理（Day4 实现）", 25));
          }
          s.setCaptureJobRunning(false);
          s.setUpdatedAt(OffsetDateTime.now());
        }
      } catch (Exception e) {
        synchronized (s) {
          s.setState(SessionState.ERROR);
          s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "拍照失败", 0));
          s.setCaptureJobRunning(false);
          s.setUpdatedAt(OffsetDateTime.now());
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

    s.setState(SessionState.IDLE);
    s.setTemplateId(null);
    s.setRawUrl(null);
    s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "已回到首页", 0));
    s.setUpdatedAt(OffsetDateTime.now());
  }
}

