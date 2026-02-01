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
   * @throws RuntimeException 如果 HTTP 非 2xx 或 success != true
   */
  public HandshakeData handshake(String baseUrl, String deviceCode, String secret) {
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
        throw new RuntimeException(
            String.format("Handshake failed: HTTP %s, response=%s",
                response.getStatusCode(), response.getBody())
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

    } catch (RestClientException e) {
      log.error("[device-api] Handshake request failed: {}", e.getMessage());
      throw new RuntimeException("Handshake request failed: " + e.getMessage(), e);
    }
  }

  /**
   * 规范化 baseUrl：去掉末尾的 /
   */
  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "";
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
   * @throws RuntimeException 如果 HTTP 非 2xx 或 success != true
   */
  public java.util.List<Map<String, Object>> listActivities(String baseUrl, Long deviceId, String deviceToken) {
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
        throw new RuntimeException(
            String.format("List activities failed: HTTP %s, response=%s",
                response.getStatusCode(), response.getBody())
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

    } catch (RestClientException e) {
      log.error("[device-api] List activities request failed: {}", e.getMessage());
      throw new RuntimeException("List activities request failed: " + e.getMessage(), e);
    }
  }
}
