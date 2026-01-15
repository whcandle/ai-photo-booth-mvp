package com.mg.booth.client;

import com.mg.booth.client.dto.AiProcessRequest;
import com.mg.booth.client.dto.AiProcessResponse;
import com.mg.booth.config.BoothProps;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiGatewayClientImpl implements AiGatewayClient {

  private final RestClient http;
  private final BoothProps props;

  public AiGatewayClientImpl(BoothProps props) {
    this.props = props;
    this.http = RestClient.builder()
      .baseUrl(props.getGatewayBaseUrl())
      .build();
  }

  public AiProcessResponse process(String idempotencyKey, AiProcessRequest req) {
    return http.post()
      .uri("/ai/v1/process")
      .contentType(MediaType.APPLICATION_JSON)
      .header("X-Device-Id", props.getDeviceId())
      .header("Idempotency-Key", idempotencyKey)
      .body(req)
      .retrieve()
      .body(AiProcessResponse.class);
  }

  @Override
  public AiProcessResponse process(String deviceId, String idempotencyKey, AiProcessRequest req) {
    return http.post()
            .uri("/ai/v1/process")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Device-Id", deviceId)
            .header("Idempotency-Key", idempotencyKey)
            .body(req)
            .retrieve()
            .body(AiProcessResponse.class);
  }
}

