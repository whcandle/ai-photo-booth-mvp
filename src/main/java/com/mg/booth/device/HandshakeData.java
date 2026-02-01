package com.mg.booth.device;

import java.time.Instant;

/**
 * Handshake 响应数据
 */
public record HandshakeData(
    Long deviceId,
    String deviceToken,
    Instant tokenExpiresAt
) {
}
