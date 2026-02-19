package com.mg.booth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration properties
 * Prefix: app
 */
@ConfigurationProperties(prefix = "app")
public class AppProps {

  /**
   * AI processing configuration
   */
  private Ai ai = new Ai();

  public Ai getAi() {
    return ai;
  }

  public void setAi(Ai ai) {
    this.ai = ai != null ? ai : new Ai();
  }

  /**
   * AI processing configuration
   */
  public static class Ai {
    /**
     * AI processing mode: v1 (legacy) or v2 (template-driven, default)
     * Default: v2
     */
    private String mode = "v2";

    /**
     * Pipeline v2 base URL (for direct /pipeline/v2/process calls).
     * Default: http://localhost:9002
     */
    private String v2BaseUrl = "http://localhost:9002";

    /**
     * AI v2 HTTP timeout in milliseconds (both connect & read).
     * Default: 60000 ms
     */
    private long v2TimeoutMs = 60000L;

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode != null ? mode : "v2";
    }

    public String getV2BaseUrl() {
      return v2BaseUrl;
    }

    public void setV2BaseUrl(String v2BaseUrl) {
      this.v2BaseUrl = (v2BaseUrl != null && !v2BaseUrl.isBlank()) ? v2BaseUrl : "http://localhost:9002";
    }

    public long getV2TimeoutMs() {
      return v2TimeoutMs;
    }

    public void setV2TimeoutMs(long v2TimeoutMs) {
      this.v2TimeoutMs = v2TimeoutMs > 0 ? v2TimeoutMs : 60000L;
    }
  }
}
