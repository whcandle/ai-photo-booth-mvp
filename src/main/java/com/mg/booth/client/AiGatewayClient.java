package com.mg.booth.client;

import com.mg.booth.client.dto.AiProcessRequest;
import com.mg.booth.client.dto.AiProcessResponse;

public interface AiGatewayClient {
    AiProcessResponse process(String deviceId, String idempotencyKey, AiProcessRequest req);
}
