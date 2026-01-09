package com.mg.booth.api;

import com.mg.booth.domain.Session;
import com.mg.booth.dto.CaptureRequest;
import com.mg.booth.dto.ConfirmRequest;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.FinishRequest;
import com.mg.booth.dto.FinishResponse;
import com.mg.booth.dto.RetryRequest;
import com.mg.booth.dto.SelectTemplateRequest;
import com.mg.booth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @PostMapping("/sessions")
  public ResponseEntity<Session> create(@Valid @RequestBody CreateSessionRequest req) {
    Session session = sessionService.create(req);
    return ResponseEntity.status(201).body(session);
  }

  @GetMapping("/sessions/{sessionId}")
  public Session get(@PathVariable String sessionId) {
    return sessionService.get(sessionId);
  }

  @PostMapping("/sessions/{sessionId}/template")
  public Session selectTemplate(@PathVariable String sessionId,
                                @Valid @RequestBody SelectTemplateRequest req) {
    return sessionService.selectTemplate(sessionId, req);
  }

  @PostMapping("/sessions/{sessionId}/finish")
  public FinishResponse finish(@PathVariable String sessionId,
                               @RequestBody(required = false) FinishRequest req) {
    String reason = (req == null) ? null : req.getReason();
    sessionService.finish(sessionId, reason);
    return new FinishResponse(true, "DONE");
  }

  @PostMapping("/sessions/{sessionId}/capture")
  public Session capture(@PathVariable String sessionId,
                         @RequestBody(required = false) CaptureRequest req) {
    return sessionService.capture(sessionId, req);
  }

  @PostMapping("/sessions/{sessionId}/retry")
  public Session retry(@PathVariable String sessionId,
                       @RequestBody(required = false) RetryRequest req) {
    String reason = (req == null) ? null : req.getReason();
    return sessionService.retry(sessionId, reason);
  }

  @PostMapping("/sessions/{sessionId}/confirm")
  public Session confirm(@PathVariable String sessionId,
                         @RequestBody(required = false) ConfirmRequest req) {
    // action 目前只支持 CONFIRM，MVP 不做别的分支
    return sessionService.confirm(sessionId);
  }
}
