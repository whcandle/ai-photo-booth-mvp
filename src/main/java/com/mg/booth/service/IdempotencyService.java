package com.mg.booth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

  public static class Record {
    private final String key;
    private final String fingerprint;
    private final String json;
    private final OffsetDateTime expiresAt;

    public Record(String key, String fingerprint, String json, OffsetDateTime expiresAt) {
      this.key = key;
      this.fingerprint = fingerprint;
      this.json = json;
      this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public String getFingerprint() { return fingerprint; }
    public String getJson() { return json; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
  }

  private final Map<String, Record> store = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;

  public IdempotencyService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public <T> T getOrCompute(String key, String fingerprint, Class<T> clazz, int ttlSeconds, Supplier<T> supplier) {
    if (key == null || key.isBlank()) {
      return supplier.get();
    }

    Record r = store.get(key);
    if (r != null && !isExpired(r)) {
      if (!fingerprint.equals(r.getFingerprint())) {
        // 同 key 不能用于不同接口/不同 session
        throw new IllegalStateException("Idempotency-Key reused for different request");
      }
      try {
        return objectMapper.readValue(r.getJson(), clazz);
      } catch (Exception e) {
        // 解析失败就当无缓存
        store.remove(key);
      }
    }

    // 计算并缓存
    T value = supplier.get();
    try {
      String json = objectMapper.writeValueAsString(value);
      store.put(key, new Record(key, fingerprint, json, OffsetDateTime.now().plusSeconds(ttlSeconds)));
    } catch (Exception ignored) {}

    return value;
  }

  public void cleanupExpired() {
    store.entrySet().removeIf(e -> isExpired(e.getValue()));
  }

  private boolean isExpired(Record r) {
    return OffsetDateTime.now().isAfter(r.getExpiresAt());
  }
}
