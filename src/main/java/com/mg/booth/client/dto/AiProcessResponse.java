package com.mg.booth.client.dto;

import java.util.Map;

public class AiProcessResponse {

  private boolean ok;
  private String requestId;
  private String previewUrl;
  private String finalUrl;
  private Map<String, Object> meta;
  private ErrorBody error;

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getPreviewUrl() {
    return previewUrl;
  }

  public void setPreviewUrl(String previewUrl) {
    this.previewUrl = previewUrl;
  }

  public String getFinalUrl() {
    return finalUrl;
  }

  public void setFinalUrl(String finalUrl) {
    this.finalUrl = finalUrl;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta;
  }

  public ErrorBody getError() {
    return error;
  }

  public void setError(ErrorBody error) {
    this.error = error;
  }

  public static class ErrorBody {
    private String code;
    private String message;
    private Map<String, Object> detail;

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public Map<String, Object> getDetail() {
      return detail;
    }

    public void setDetail(Map<String, Object> detail) {
      this.detail = detail;
    }
  }
}

