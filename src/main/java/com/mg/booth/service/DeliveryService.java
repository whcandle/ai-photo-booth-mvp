package com.mg.booth.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeliveryService {

  public static class DeliveryRecord {
    private final String token;
    private final String sessionId;
    private final OffsetDateTime expiresAt;

    public DeliveryRecord(String token, String sessionId, OffsetDateTime expiresAt) {
      this.token = token;
      this.sessionId = sessionId;
      this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public String getSessionId() { return sessionId; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
  }

  private final Map<String, DeliveryRecord> tokenStore = new ConcurrentHashMap<>();

  public DeliveryRecord createToken(String sessionId, int ttlSeconds) {
    String token = "tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds);
    DeliveryRecord r = new DeliveryRecord(token, sessionId, expiresAt);
    tokenStore.put(token, r);
    return r;
  }

  public DeliveryRecord getValid(String token) {   // may return null
    DeliveryRecord r = tokenStore.get(token);
    if (r == null) return null;
    if (OffsetDateTime.now().isAfter(r.getExpiresAt())) {
      tokenStore.remove(token);
      return null;
    }
    return r;
  }

  public void cleanupExpired() {
    OffsetDateTime now = OffsetDateTime.now();
    tokenStore.entrySet().removeIf(e -> now.isAfter(e.getValue().getExpiresAt()));
  }
}
