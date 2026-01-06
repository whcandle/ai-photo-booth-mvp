package com.mg.booth.domain;

public class TemplateSummary {
  private String templateId;
  private String name;
  private boolean enabled;

  public TemplateSummary() {}

  public TemplateSummary(String templateId, String name, boolean enabled) {
    this.templateId = templateId;
    this.name = name;
    this.enabled = enabled;
  }

  public String getTemplateId() { return templateId; }
  public void setTemplateId(String templateId) { this.templateId = templateId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
