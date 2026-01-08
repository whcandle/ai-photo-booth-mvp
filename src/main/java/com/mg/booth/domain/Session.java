package com.mg.booth.domain;

import java.time.OffsetDateTime;

public class Session {
  private String sessionId;  // UUID v4
  private SessionState state;   // SELECTING, RENDERING, COMPLETED, FAILED
  private String templateId;
  private Integer attemptIndex;   // current attempt index
  private Integer maxRetries;
  private Integer retriesLeft;
  private Integer countdownSeconds;
  private SessionProgress progress;
  private String rawUrl;
  private String previewUrl;
  private String finalUrl;

  // Day3: 防重复启动 capture job
  private boolean captureJobRunning;

  // 用于超时判断
  private OffsetDateTime stateEnteredAt;

  // 防重复启动 AI job
  private boolean aiJobRunning;

  // 对齐 OpenAPI 的 error 字段
  private com.mg.booth.dto.ApiError error;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  public Session() {}

  public Session(String sessionId, SessionState state, String templateId,
                 Integer attemptIndex, Integer maxRetries, Integer retriesLeft,
                 Integer countdownSeconds, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    this.sessionId = sessionId;
    this.state = state;
    this.templateId = templateId;
    this.attemptIndex = attemptIndex;
    this.maxRetries = maxRetries;
    this.retriesLeft = retriesLeft;
    this.countdownSeconds = countdownSeconds;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }

  public SessionState getState() { return state; }
  public void setState(SessionState state) { this.state = state; }

  public String getTemplateId() { return templateId; }
  public void setTemplateId(String templateId) { this.templateId = templateId; }

  public Integer getAttemptIndex() { return attemptIndex; }
  public void setAttemptIndex(Integer attemptIndex) { this.attemptIndex = attemptIndex; }

  public Integer getMaxRetries() { return maxRetries; }
  public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

  public Integer getRetriesLeft() { return retriesLeft; }
  public void setRetriesLeft(Integer retriesLeft) { this.retriesLeft = retriesLeft; }

  public Integer getCountdownSeconds() { return countdownSeconds; }
  public void setCountdownSeconds(Integer countdownSeconds) { this.countdownSeconds = countdownSeconds; }

  public SessionProgress getProgress() { return progress; }
  public void setProgress(SessionProgress progress) { this.progress = progress; }

  public String getRawUrl() { return rawUrl; }
  public void setRawUrl(String rawUrl) { this.rawUrl = rawUrl; }

  public String getPreviewUrl() { return previewUrl; }
  public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

  public String getFinalUrl() { return finalUrl; }
  public void setFinalUrl(String finalUrl) { this.finalUrl = finalUrl; }

  public boolean isCaptureJobRunning() { return captureJobRunning; }
  public void setCaptureJobRunning(boolean captureJobRunning) { this.captureJobRunning = captureJobRunning; }

  public OffsetDateTime getStateEnteredAt() { return stateEnteredAt; }
  public void setStateEnteredAt(OffsetDateTime stateEnteredAt) { this.stateEnteredAt = stateEnteredAt; }

  public boolean isAiJobRunning() { return aiJobRunning; }
  public void setAiJobRunning(boolean aiJobRunning) { this.aiJobRunning = aiJobRunning; }

  public com.mg.booth.dto.ApiError getError() { return error; }
  public void setError(com.mg.booth.dto.ApiError error) { this.error = error; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

