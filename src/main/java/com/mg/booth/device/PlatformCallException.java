package com.mg.booth.device;

/**
 * Platform API 调用异常
 * 用于区分不同类型的平台调用错误，便于上层处理并返回正确的 HTTP 状态码
 */
public class PlatformCallException extends RuntimeException {
  
  private final int httpStatus;
  private final String url;
  private final String reason;
  private final Object responseBody;

  /**
   * 创建 PlatformCallException
   * 
   * @param httpStatus HTTP 状态码，如果没有则使用 -1
   * @param url 请求的 URL
   * @param reason 错误原因（如 "unauthorized" / "timeout" / "dns" / "connection_refused" / "http_error" / "unreachable"）
   * @param message 错误消息
   * @param responseBody 响应体（可选，可能为 null）
   */
  public PlatformCallException(int httpStatus, String url, String reason, String message, Object responseBody) {
    super(message);
    this.httpStatus = httpStatus;
    this.url = url;
    this.reason = reason;
    this.responseBody = responseBody;
  }

  /**
   * 创建 PlatformCallException（无响应体）
   */
  public PlatformCallException(int httpStatus, String url, String reason, String message) {
    this(httpStatus, url, reason, message, null);
  }

  /**
   * 创建 PlatformCallException（无 HTTP 状态码）
   */
  public PlatformCallException(String url, String reason, String message) {
    this(-1, url, reason, message, null);
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public String getUrl() {
    return url;
  }

  public String getReason() {
    return reason;
  }

  public Object getResponseBody() {
    return responseBody;
  }

  /**
   * 检查是否是未授权错误（401）
   */
  public boolean isUnauthorized() {
    return httpStatus == 401 || "unauthorized".equals(reason);
  }

  /**
   * 检查是否是服务不可达（503）
   */
  public boolean isUnreachable() {
    return httpStatus == 503 || "unreachable".equals(reason);
  }
}
