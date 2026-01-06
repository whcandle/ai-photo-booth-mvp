package com.mg.booth.service;

import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionState;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.SelectTemplateRequest;
import com.mg.booth.exception.ConflictException;
import com.mg.booth.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

  private final Map<String, Session> store = new ConcurrentHashMap<>();
  private final TemplateService templateService;
  private final SessionStateMachine sm;

  public SessionService(TemplateService templateService, SessionStateMachine sm) {
    this.templateService = templateService;
    this.sm = sm;
  }

  public Session create(CreateSessionRequest req) {
    String id = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    OffsetDateTime now = OffsetDateTime.now();

    // In MVP, create means: IDLE -> SELECTING
    SessionState from = SessionState.IDLE;
    SessionState to = SessionState.SELECTING;
    if (!sm.canTransition(from, to)) {
      throw new ConflictException("INVALID_STATE", "Cannot create session");
    }

    Session s = new Session(
      id,
      SessionState.SELECTING,
      null,                  // templateId
      0,                     // attemptIndex
      req.getMaxRetries(),
      req.getMaxRetries(),
      req.getCountdownSeconds(),
      now,
      now
    );

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

    // validate template exists (Day1 templates are in-memory)
    boolean exists = templateService.listTemplates().stream()
      .anyMatch(t -> t.isEnabled() && t.getTemplateId().equals(req.getTemplateId()));
    if (!exists) {
      throw new NotFoundException("Template not found or disabled: " + req.getTemplateId());
    }

    // state transition: SELECTING -> COUNTDOWN
    if (!sm.canTransition(s.getState(), SessionState.COUNTDOWN)) {
      throw new ConflictException("INVALID_STATE", "Action not allowed in current state: " + s.getState());
    }

    s.setTemplateId(req.getTemplateId());
    s.setState(SessionState.COUNTDOWN);
    s.setUpdatedAt(OffsetDateTime.now());
    return s;
  }

  public void finish(String sessionId, String reason) {
    Session s = get(sessionId);

    // allow recovery to IDLE from many states (see state machine)
    if (!sm.canTransition(s.getState(), SessionState.IDLE)) {
      throw new ConflictException("INVALID_STATE", "Finish not allowed in current state: " + s.getState());
    }

    s.setState(SessionState.IDLE);
    s.setTemplateId(null);
    s.setUpdatedAt(OffsetDateTime.now());
    // Day2: 不删除 session，方便你调试。Day6+ 可以改成归档/删除/TTL。
  }
}
