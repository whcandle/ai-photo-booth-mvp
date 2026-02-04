package com.mg.booth.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Platform 设备 API 客户端（仅实现 handshake）
 */
@Component("devicePlatformDeviceApiClient")
public class PlatformDeviceApiClient {

  private static final Logger log = LoggerFactory.getLogger(PlatformDeviceApiClient.class);

  private final RestTemplate restTemplate;

  public PlatformDeviceApiClient(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder.build();
  }

  /**
   * 执行 handshake
   *
   * @param baseUrl Platform API 基础 URL（例如：http://127.0.0.1:8089）
   * @param deviceCode 设备编码
   * @param secret 设备密钥
   * @return HandshakeData 包含 deviceId, deviceToken, tokenExpiresAt
   * @throws PlatformCallException 如果平台调用失败
   * @throws IllegalArgumentException 如果 baseUrl 为空
   */
  public HandshakeData handshake(String baseUrl, String deviceCode, String secret) {
    // 检查 baseUrl 是否为空
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("platformBaseUrl not configured");
    }
    
    String url = normalizeBaseUrl(baseUrl) + "/api/v1/device/handshake";

    // 构建请求体
    Map<String, String> requestBody = Map.of(
        "deviceCode", deviceCode,
        "secret", secret
    );

    // 设置请求头
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

    log.debug("[device-api] Handshake request: url={}, deviceCode={}", url, deviceCode);

    try {
      ResponseEntity<Map> response = restTemplate.exchange(
          url,
          HttpMethod.POST,
          requestEntity,
          Map.class
      );

      // 检查 HTTP 状态码
      if (!response.getStatusCode().is2xxSuccessful()) {
        int statusCode = response.getStatusCode().value();
        Object responseBody = response.getBody();
        String reason = statusCode == 401 ? "unauthorized" : "http_error";
        log.error("[device-api] Handshake failed: url={}, status={}, reason={}", url, statusCode, reason);
        throw new PlatformCallException(
            statusCode, url, reason,
            String.format("Handshake failed: HTTP %s", response.getStatusCode()),
            responseBody
        );
      }

      Map<String, Object> responseBody = response.getBody();
      if (responseBody == null) {
        throw new RuntimeException("Handshake failed: empty response body");
      }

      // 检查 success 字段
      Object successObj = responseBody.get("success");
      if (!(successObj instanceof Boolean) || !((Boolean) successObj)) {
        String message = String.valueOf(responseBody.get("message"));
        throw new RuntimeException(
            String.format("Handshake failed: success=false, message=%s, response=%s",
                message, responseBody)
        );
      }

      // 提取 data
      Object dataObj = responseBody.get("data");
      if (!(dataObj instanceof Map)) {
        throw new RuntimeException("Handshake failed: data field is not a map, response=" + responseBody);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) dataObj;

      // 提取字段
      Long deviceId = extractLong(data, "deviceId");
      String deviceToken = extractString(data, "deviceToken");
      Long expiresIn = extractLong(data, "expiresIn"); // seconds

      // 计算 tokenExpiresAt
      Instant tokenExpiresAt = expiresIn != null
          ? Instant.now().plusSeconds(expiresIn)
          : null;

      log.info("[device-api] Handshake success: deviceId={}, expiresIn={}s", deviceId, expiresIn);

      return new HandshakeData(deviceId, deviceToken, tokenExpiresAt);

    } catch (HttpStatusCodeException e) {
      // HTTP 状态码异常（4xx, 5xx）
      int statusCode = e.getStatusCode().value();
      String reason = statusCode == 401 ? "unauthorized" : "http_error";
      Object responseBody = null;
      try {
        responseBody = e.getResponseBodyAs(Map.class);
      } catch (Exception ex) {
        // 如果无法解析响应体，使用原始字符串
        responseBody = e.getResponseBodyAsString();
      }
      log.error("[device-api] Handshake failed: url={}, status={}, reason={}", url, statusCode, reason);
      throw new PlatformCallException(
          statusCode, url, reason,
          String.format("Handshake failed: HTTP %s, %s", e.getStatusCode(), e.getMessage()),
          responseBody
      );
    } catch (ResourceAccessException e) {
      // 资源访问异常（超时、连接失败、DNS 解析失败等）
      String reason = "unreachable";
      String message = e.getMessage();
      
      // 检查 cause 是否是 UnknownHostException
      Throwable cause = e.getCause();
      if (cause != null) {
        String causeClassName = cause.getClass().getSimpleName();
        if (causeClassName.contains("UnknownHost") || causeClassName.contains("UnknownHostException")) {
          reason = "dns";
        } else if (causeClassName.contains("ConnectException") || causeClassName.contains("ConnectionRefused")) {
          reason = "connection_refused";
        } else if (causeClassName.contains("SocketTimeout") || causeClassName.contains("ReadTimeout")) {
          reason = "timeout";
        }
      }
      
      // 如果 cause 检查没有识别，再检查 message
      if ("unreachable".equals(reason) && message != null) {
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("timeout")) {
          reason = "timeout";
        } else if (lowerMessage.contains("unknownhost") || lowerMessage.contains("dns") || 
                   lowerMessage.contains("unknown host")) {
          reason = "dns";
        } else if (lowerMessage.contains("connection refused") || lowerMessage.contains("connectionreset")) {
          reason = "connection_refused";
        }
      }
      
      log.error("[device-api] Handshake failed: url={}, status=503, reason={}, message={}", url, reason, message);
      throw new PlatformCallException(
          503, url, reason,
          String.format("Handshake failed: %s", message)
      );
    } catch (RestClientException e) {
      // 其他 RestClientException
      log.error("[device-api] Handshake failed: url={}, status=503, reason=unreachable, message={}", url, e.getMessage());
      throw new PlatformCallException(
          503, url, "unreachable",
          String.format("Handshake failed: %s", e.getMessage())
      );
    }
  }

  /**
   * 规范化 baseUrl：去掉末尾的 /
   * 
   * @param baseUrl Platform API 基础 URL
   * @return 规范化后的 baseUrl（去掉末尾的 /）
   * @throws IllegalArgumentException 如果 baseUrl 为空
   */
  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("platformBaseUrl not configured");
    }
    String normalized = baseUrl.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private Long extractLong(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    throw new RuntimeException("Field " + key + " is not a number: " + value);
  }

  private String extractString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }

  /**
   * 获取设备活动列表
   *
   * @param baseUrl Platform API 基础 URL（例如：http://127.0.0.1:8089）
   * @param deviceId 设备ID
   * @param deviceToken 设备 token
   * @return 活动列表（Map 数组）
   * @throws PlatformCallException 如果平台调用失败
   * @throws IllegalArgumentException 如果 baseUrl 为空
   */
  public java.util.List<Map<String, Object>> listActivities(String baseUrl, Long deviceId, String deviceToken) {
    // 检查 baseUrl 是否为空
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("platformBaseUrl not configured");
    }
    
    String url = normalizeBaseUrl(baseUrl) + "/api/v1/device/" + deviceId + "/activities";

    // 设置请求头
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + deviceToken);
    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

    log.debug("[device-api] List activities request: url={}, deviceId={}", url, deviceId);

    try {
      ResponseEntity<Map> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          requestEntity,
          Map.class
      );

      // 检查 HTTP 状态码
      if (!response.getStatusCode().is2xxSuccessful()) {
        int statusCode = response.getStatusCode().value();
        Object responseBody = response.getBody();
        String reason = statusCode == 401 ? "unauthorized" : "http_error";
        log.error("[device-api] List activities failed: url={}, status={}, reason={}", url, statusCode, reason);
        throw new PlatformCallException(
            statusCode, url, reason,
            String.format("List activities failed: HTTP %s", response.getStatusCode()),
            responseBody
        );
      }

      Map<String, Object> responseBody = response.getBody();
      if (responseBody == null) {
        throw new RuntimeException("List activities failed: empty response body");
      }

      // 检查 success 字段
      Object successObj = responseBody.get("success");
      if (!(successObj instanceof Boolean) || !((Boolean) successObj)) {
        String message = String.valueOf(responseBody.get("message"));
        throw new RuntimeException(
            String.format("List activities failed: success=false, message=%s, response=%s",
                message, responseBody)
        );
      }

      // 提取 data
      Object dataObj = responseBody.get("data");
      if (dataObj == null) {
        return java.util.List.of();
      }

      if (!(dataObj instanceof java.util.List)) {
        throw new RuntimeException("List activities failed: data field is not a list, response=" + responseBody);
      }

      @SuppressWarnings("unchecked")
      java.util.List<Map<String, Object>> activities = (java.util.List<Map<String, Object>>) dataObj;

      log.info("[device-api] List activities success: count={}", activities.size());

      return activities;

    } catch (HttpStatusCodeException e) {
      // HTTP 状态码异常（4xx, 5xx）
      int statusCode = e.getStatusCode().value();
      String reason = statusCode == 401 ? "unauthorized" : "http_error";
      Object responseBody = null;
      try {
        responseBody = e.getResponseBodyAs(Map.class);
      } catch (Exception ex) {
        // 如果无法解析响应体，使用原始字符串
        responseBody = e.getResponseBodyAsString();
      }
      log.error("[device-api] List activities failed: url={}, status={}, reason={}", url, statusCode, reason);
      throw new PlatformCallException(
          statusCode, url, reason,
          String.format("List activities failed: HTTP %s, %s", e.getStatusCode(), e.getMessage()),
          responseBody
      );
    } catch (ResourceAccessException e) {
      // 资源访问异常（超时、连接失败、DNS 解析失败等）
      String reason = "unreachable";
      String message = e.getMessage();
      
      // 检查 cause 是否是 UnknownHostException
      Throwable cause = e.getCause();
      if (cause != null) {
        String causeClassName = cause.getClass().getSimpleName();
        if (causeClassName.contains("UnknownHost") || causeClassName.contains("UnknownHostException")) {
          reason = "dns";
        } else if (causeClassName.contains("ConnectException") || causeClassName.contains("ConnectionRefused")) {
          reason = "connection_refused";
        } else if (causeClassName.contains("SocketTimeout") || causeClassName.contains("ReadTimeout")) {
          reason = "timeout";
        }
      }
      
      // 如果 cause 检查没有识别，再检查 message
      if ("unreachable".equals(reason) && message != null) {
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("timeout")) {
          reason = "timeout";
        } else if (lowerMessage.contains("unknownhost") || lowerMessage.contains("dns") || 
                   lowerMessage.contains("unknown host")) {
          reason = "dns";
        } else if (lowerMessage.contains("connection refused") || lowerMessage.contains("connectionreset")) {
          reason = "connection_refused";
        }
      }
      
      log.error("[device-api] List activities failed: url={}, status=503, reason={}, message={}", url, reason, message);
      throw new PlatformCallException(
          503, url, reason,
          String.format("List activities failed: %s", message)
      );
    } catch (RestClientException e) {
      // 其他 RestClientException
      log.error("[device-api] List activities failed: url={}, status=503, reason=unreachable, message={}", url, e.getMessage());
      throw new PlatformCallException(
          503, url, "unreachable",
          String.format("List activities failed: %s", e.getMessage())
      );
    }
  }
}
