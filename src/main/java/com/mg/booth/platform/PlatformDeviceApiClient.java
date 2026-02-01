package com.mg.booth.platform;

import com.mg.booth.platform.dto.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class PlatformDeviceApiClient {

  private final RestClient http;

  private static final ParameterizedTypeReference<ApiResponse<HandshakeData>> HANDSHAKE_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResponse<List<DeviceActivityDto>>> ACTIVITIES_TYPE =
      new ParameterizedTypeReference<>() {};

  public PlatformDeviceApiClient() {
    this.http = RestClient.builder().build();
  }

  public HandshakeData handshake(String platformBaseUrl, String deviceCode, String secret) {
    ApiResponse<HandshakeData> resp = http.post()
        .uri(platformBaseUrl + "/api/v1/device/handshake")
        .body(new HandshakeRequest(deviceCode, secret))
        .retrieve()
        .body(HANDSHAKE_TYPE);

    if (resp == null) throw new IllegalStateException("handshake: empty response");
    if (!resp.isSuccess()) throw new IllegalStateException("handshake failed: " + resp.getMessage());
    if (resp.getData() == null) throw new IllegalStateException("handshake: data is null");

    return resp.getData();
  }

  public List<DeviceActivityDto> listActivities(String platformBaseUrl, Long deviceId, String token) {
    try {
      ApiResponse<List<DeviceActivityDto>> resp = http.get()
          .uri(platformBaseUrl + "/api/v1/device/" + deviceId + "/activities")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .retrieve()
          .body(ACTIVITIES_TYPE);

      if (resp == null) throw new IllegalStateException("activities: empty response");
      if (!resp.isSuccess()) throw new IllegalStateException("activities failed: " + resp.getMessage());
      return resp.getData() == null ? List.of() : resp.getData();

    } catch (HttpClientErrorException e) {
      // 平台约定 401：缺 token/无效 token/deviceId 不匹配
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        throw e; // 交给上层做"重握手后重试"
      }
      throw e;
    }
  }
}
