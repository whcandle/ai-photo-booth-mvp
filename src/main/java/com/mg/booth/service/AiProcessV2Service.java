package com.mg.booth.service;

import com.mg.booth.config.BoothProps;
import com.mg.booth.domain.Session;
import com.mg.booth.domain.V2TemplateRef;
import com.mg.booth.dto.ApiError;
import com.mg.booth.domain.SessionProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AI Processing V2 Service
 * Template-driven AI processing logic (v2).
 *
 * Responsibilities:
 * - Read rawPath from session.rawUrl
 * - Resolve v2 template metadata via TemplateResolveService
 * - Call AiGatewayV2Client to process image
 * - Write previewUrl/finalUrl back to session (gatewayized URL)
 * - Update session progress and timestamps
 * - On failure, set ApiError with PROCESSING_FAILED and detailed reason
 */
@Service
public class AiProcessV2Service {

  private static final Logger log = LoggerFactory.getLogger(AiProcessV2Service.class);

  private final TemplateResolveService templateResolveService;
  private final AiGatewayV2Client aiGatewayV2Client;
  private final BoothProps boothProps;

  public AiProcessV2Service(
      TemplateResolveService templateResolveService,
      AiGatewayV2Client aiGatewayV2Client,
      BoothProps boothProps
  ) {
    this.templateResolveService = templateResolveService;
    this.aiGatewayV2Client = aiGatewayV2Client;
    this.boothProps = boothProps;
  }

  /**
   * Process session with v2 template-driven logic.
   *
   * @param session Session to process (must already be in PROCESSING state with rawUrl set)
   */
  public void process(Session session) {
    String sessionId = session.getSessionId();
    String templateId = session.getTemplateId();
    Integer attemptIndex = session.getAttemptIndex();

    log.info("[ai-process-v2] Processing session: sessionId={}, templateId={}, attemptIndex={}",
        sessionId, templateId, attemptIndex);

    String rawPath = session.getRawUrl();
    if (rawPath == null || rawPath.isBlank()) {
      String reason = "rawUrl is not set on session " + sessionId;
      log.error("[ai-process-v2] {}", reason);
      session.setError(new ApiError("PROCESSING_FAILED", "Capture/AI failed", Map.of("reason", reason)));
      return;
    }

    try {
      // 1) Resolve v2 template metadata from local index.json
      V2TemplateRef ref = templateResolveService.resolveForV2(templateId);

      // 2) Call pipeline v2 via AiGatewayV2Client
      AiGatewayV2Client.Result result = aiGatewayV2Client.process(
          ref.getTemplateCode(),
          ref.getVersionSemver(),
          ref.getDownloadUrl(),
          ref.getChecksumSha256(),
          rawPath
      );

      if (!result.isOk()) {
        String reason = "pipeline v2 failed: " +
            (result.getErrorCode() != null ? result.getErrorCode() : "UNKNOWN") +
            " - " +
            (result.getErrorMessage() != null ? result.getErrorMessage() : "no message");
        log.error("[ai-process-v2] {}", reason);
        session.setError(new ApiError("PROCESSING_FAILED", "Capture/AI failed", Map.of("reason", reason)));
        return;
      }

      // 3) Gatewayize URLs (use gatewayBaseUrl if preview/final are relative paths)
      String gatewayBase = boothProps.getGatewayBaseUrl();
      String previewUrl = gatewayizeUrl(gatewayBase, result.getPreviewUrl());
      String finalUrl = gatewayizeUrl(gatewayBase, result.getFinalUrl());

      session.setPreviewUrl(previewUrl);
      session.setFinalUrl(finalUrl);

      // 更新进度与时间戳（状态切换由 SessionService 负责）
      session.setProgress(new SessionProgress(SessionProgress.Step.FINAL_READY, "生成成品…", 95));
      session.setUpdatedAt(OffsetDateTime.now());

      log.info("[ai-process-v2] Success: sessionId={}, previewUrl={}, finalUrl={}",
          sessionId, previewUrl, finalUrl);

    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.error("[ai-process-v2] Exception: {}", msg, e);
      session.setError(new ApiError("PROCESSING_FAILED", "Capture/AI failed", Map.of("reason", msg)));
    }
  }

  private String gatewayizeUrl(String gatewayBaseUrl, String url) {
    if (url == null || url.isBlank()) {
      return url;
    }
    // Already absolute URL
    if (url.startsWith("http://") || url.startsWith("https://")) {
      return url;
    }
    // Relative path, ensure leading slash
    String path = url.startsWith("/") ? url : ("/" + url);
    if (gatewayBaseUrl == null || gatewayBaseUrl.isBlank()) {
      // Fallback: use localhost:9001 as default gateway
      return "http://127.0.0.1:9001" + path;
    }
    String base = gatewayBaseUrl.trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + path;
  }
}

