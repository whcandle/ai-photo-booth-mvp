package com.mg.booth.api;

import com.mg.booth.domain.Session;
import com.mg.booth.dto.CaptureRequest;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.FinishRequest;
import com.mg.booth.dto.FinishResponse;
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
}
