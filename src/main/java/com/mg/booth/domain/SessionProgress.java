package com.mg.booth.domain;

public class SessionProgress {

  public enum Step {
    NONE,
    CAPTURE_DONE,
    AI_QUEUED,
    AI_PROCESSING,
    PREVIEW_READY,
    FINAL_READY,
    DELIVERY_READY
  }

  private Step step;
  private String message;
  private Integer percent;

  public SessionProgress() {}

  public SessionProgress(Step step, String message, Integer percent) {
    this.step = step;
    this.message = message;
    this.percent = percent;
  }

  public Step getStep() { return step; }
  public void setStep(Step step) { this.step = step; }

  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }

  public Integer getPercent() { return percent; }
  public void setPercent(Integer percent) { this.percent = percent; }
}

