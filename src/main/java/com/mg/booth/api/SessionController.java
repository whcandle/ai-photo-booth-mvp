package com.mg.booth.api;

import com.mg.booth.domain.Session;
import com.mg.booth.dto.CaptureRequest;
import com.mg.booth.dto.ConfirmRequest;
import com.mg.booth.dto.CreateSessionRequest;
import com.mg.booth.dto.FinishRequest;
import com.mg.booth.dto.FinishResponse;
import com.mg.booth.dto.RetryRequest;
import com.mg.booth.dto.SelectTemplateRequest;
import com.mg.booth.service.IdempotencyService;
import com.mg.booth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

  private final SessionService sessionService;
  private final IdempotencyService idempotencyService;

  public SessionController(SessionService sessionService, IdempotencyService idempotencyService) {
    this.sessionService = sessionService;
    this.idempotencyService = idempotencyService;
  }

  private String idemKey(String header) {
    return (header == null || header.isBlank()) ? null : header.trim();
  }

  @PostMapping("/sessions")
  public ResponseEntity<Session> create(
    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
    @Valid @RequestBody CreateSessionRequest req
  ) {
    String key = idemKey(idem);
    String fp = "POST /api/v1/sessions";
    Session session = idempotencyService.getOrCompute(key, fp, Session.class, 120,
      () -> sessionService.create(req)
    );
    return ResponseEntity.status(201).body(session);
  }

  @GetMapping("/sessions/{sessionId}")
  public Session get(@PathVariable String sessionId) {
    return sessionService.get(sessionId);
  }

  @PostMapping("/sessions/{sessionId}/template")
  public Session selectTemplate(
    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
    @PathVariable String sessionId,
    @Valid @RequestBody SelectTemplateRequest req
  ) {
    String key = idemKey(idem);
    String fp = "POST /api/v1/sessions/" + sessionId + "/template:" + req.getTemplateId();
    return idempotencyService.getOrCompute(key, fp, Session.class, 120,
      () -> sessionService.selectTemplate(sessionId, req)
    );
  }

  @PostMapping("/sessions/{sessionId}/finish")
  public FinishResponse finish(
    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
    @PathVariable String sessionId,
    @RequestBody(required = false) FinishRequest req
  ) {
    String key = idemKey(idem);
    String reason = (req == null) ? null : req.getReason();
    String fp = "POST /api/v1/sessions/" + sessionId + "/finish:" + (reason == null ? "" : reason);

    return idempotencyService.getOrCompute(key, fp, FinishResponse.class, 120, () -> {
      sessionService.finish(sessionId, reason);
      return new FinishResponse(true, "DONE");
    });
  }

  @PostMapping("/sessions/{sessionId}/capture")
  public Session capture(
    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
    @PathVariable String sessionId,
    @RequestBody(required = false) CaptureRequest req
  ) {
    String key = idemKey(idem);
    Integer attempt = (req == null) ? null : req.getAttemptIndex();
    String fp = "POST /api/v1/sessions/" + sessionId + "/capture:" + (attempt == null ? "" : attempt);

    return idempotencyService.getOrCompute(key, fp, Session.class, 120,
      () -> sessionService.capture(sessionId, req)
    );
  }

  @PostMapping("/sessions/{sessionId}/retry")
  public Session retry(
    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
    @PathVariable String sessionId,
    @RequestBody(required = false) RetryRequest req
  ) {
    String key = idemKey(idem);
    String reason = (req == null) ? null : req.getReason();
    String fp = "POST /api/v1/sessions/" + sessionId + "/retry:" + (reason == null ? "" : reason);

    return idempotencyService.getOrCompute(key, fp, Session.class, 120,
      () -> sessionService.retry(sessionId, reason)
    );
  }

  @PostMapping("/sessions/{sessionId}/confirm")
  public Session confirm(
    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
    @PathVariable String sessionId,
    @RequestBody(required = false) ConfirmRequest req
  ) {
    String key = idemKey(idem);
    String fp = "POST /api/v1/sessions/" + sessionId + "/confirm";
    return idempotencyService.getOrCompute(key, fp, Session.class, 120,
      () -> sessionService.confirm(sessionId)
    );
  }
}
