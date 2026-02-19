package com.mg.booth.service;

import com.mg.booth.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AI Gateway V2 client.
 * Calls pipeline /pipeline/v2/process directly (default: http://localhost:9002).
 */
@Service
public class AiGatewayV2Client {

  private static final Logger log = LoggerFactory.getLogger(AiGatewayV2Client.class);

  private final RestTemplate restTemplate;
  private final AppProps appProps;

  public AiGatewayV2Client(RestTemplateBuilder restTemplateBuilder, AppProps appProps) {
    this.appProps = appProps;
    long timeoutMs = appProps.getAi() != null ? appProps.getAi().getV2TimeoutMs() : 60000L;
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofMillis(timeoutMs))
        .setReadTimeout(Duration.ofMillis(timeoutMs))
        .build();
  }

  public Result process(String templateCode,
                        String versionSemver,
                        String downloadUrl,
                        String checksumSha256,
                        String rawPath) {

    String traceId = "v2-" + System.currentTimeMillis();

    String baseUrl = resolveBaseUrl();
    String url = normalizeBaseUrl(baseUrl) + "/pipeline/v2/process";

    Map<String, Object> body = new HashMap<>();
    body.put("templateCode", templateCode);
    body.put("versionSemver", versionSemver);
    body.put("downloadUrl", downloadUrl);
    body.put("checksumSha256", checksumSha256);
    body.put("rawPath", rawPath);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

    log.info("[ai-v2-client] [{}] Request: url={}, template={}@{}, rawPath={}",
        traceId, url, templateCode, versionSemver, rawPath);

    try {
      ResponseEntity<Map> resp = restTemplate.exchange(
          url,
          HttpMethod.POST,
          entity,
          Map.class
      );

      Map<String, Object> bodyMap = resp.getBody();
      String jobId = bodyMap != null ? str(bodyMap.get("jobId")) : null;

      if (!resp.getStatusCode().is2xxSuccessful()) {
        int status = resp.getStatusCode().value();
        log.error("[ai-v2-client] [{}] HTTP error: status={}, jobId={}, body={}",
            traceId, status, jobId, bodyMap);
        return Result.httpError(status, "HTTP_" + status,
            "HTTP error from pipeline v2, status=" + status);
      }

      if (bodyMap == null) {
        log.error("[ai-v2-client] [{}] Empty response body", traceId);
        return Result.httpError(resp.getStatusCode().value(), "EMPTY_BODY", "Empty response body from pipeline v2");
      }

      Object okObj = bodyMap.get("ok");
      boolean ok = (okObj instanceof Boolean) && (Boolean) okObj;

      if (!ok) {
        Map<String, Object> error = getMap(bodyMap, "error");
        String code = error != null ? String.valueOf(error.getOrDefault("code", "PIPELINE_ERROR")) : "PIPELINE_ERROR";
        String message = error != null ? String.valueOf(error.getOrDefault("message", "pipeline v2 failed")) : "pipeline v2 failed";
        log.error("[ai-v2-client] [{}] Pipeline v2 failed: jobId={}, code={}, message={}",
            traceId, jobId, code, message);
        return Result.fail(code, message);
      }

      Map<String, Object> outputs = getMap(bodyMap, "outputs");
      String previewUrl = outputs != null ? str(outputs.get("previewUrl")) : null;
      String finalUrl = outputs != null ? str(outputs.get("finalUrl")) : null;
      Map<String, Object> timing = getMap(bodyMap, "timing");

      log.info("[ai-v2-client] [{}] Success: jobId={}, previewUrl={}, finalUrl={}",
          traceId, jobId, previewUrl, finalUrl);

      return Result.ok(previewUrl, finalUrl, timing);

    } catch (HttpStatusCodeException e) {
      int status = e.getStatusCode().value();
      String msg = e.getMessage();
      log.error("[ai-v2-client] [{}] HTTP error: status={}, message={}", traceId, status, msg);
      return Result.httpError(status, "HTTP_" + status, msg);
    } catch (ResourceAccessException e) {
      String msg = e.getMessage();
      log.error("[ai-v2-client] [{}] Resource access error: {}", traceId, msg);
      return Result.httpError(503, "UNREACHABLE", msg);
    } catch (RestClientException e) {
      String msg = e.getMessage();
      log.error("[ai-v2-client] [{}] Rest client error: {}", traceId, msg);
      return Result.httpError(503, "UNREACHABLE", msg);
    } catch (Exception e) {
      String msg = e.getMessage();
      log.error("[ai-v2-client] [{}] Unexpected error: {}", traceId, msg, e);
      return Result.httpError(500, "INTERNAL_ERROR", msg);
    }
  }

  private String resolveBaseUrl() {
    if (appProps != null && appProps.getAi() != null && appProps.getAi().getV2BaseUrl() != null) {
      return appProps.getAi().getV2BaseUrl();
    }
    return "http://localhost:9002";
  }

  private String normalizeBaseUrl(String baseUrl) {
    String normalized = baseUrl != null ? baseUrl.trim() : "";
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getMap(Map<String, Object> parent, String key) {
    Object v = parent != null ? parent.get(key) : null;
    if (v instanceof Map<?, ?>) {
      return (Map<String, Object>) v;
    }
    return null;
  }

  private String str(Object v) {
    return v != null ? String.valueOf(v) : null;
  }

  /**
   * Minimal result DTO from pipeline v2.
   */
  public static class Result {
    private final boolean ok;
    private final String previewUrl;
    private final String finalUrl;
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> timing;

    private Result(boolean ok, String previewUrl, String finalUrl,
                   String errorCode, String errorMessage, Map<String, Object> timing) {
      this.ok = ok;
      this.previewUrl = previewUrl;
      this.finalUrl = finalUrl;
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
      this.timing = timing;
    }

    public static Result ok(String previewUrl, String finalUrl, Map<String, Object> timing) {
      return new Result(true, previewUrl, finalUrl, null, null, timing);
    }

    public static Result fail(String errorCode, String errorMessage) {
      return new Result(false, null, null, errorCode, errorMessage, null);
    }

    public static Result httpError(int status, String errorCode, String message) {
      String code = errorCode != null ? errorCode : "HTTP_" + status;
      String msg = message != null ? message : "HTTP error " + status;
      return new Result(false, null, null, code, msg, null);
    }

    public boolean isOk() {
      return ok;
    }

    public String getPreviewUrl() {
      return previewUrl;
    }

    public String getFinalUrl() {
      return finalUrl;
    }

    public String getErrorCode() {
      return errorCode;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public Map<String, Object> getTiming() {
      return timing;
    }
  }
}

