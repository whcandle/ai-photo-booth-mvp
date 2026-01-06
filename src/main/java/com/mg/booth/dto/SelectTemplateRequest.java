package com.mg.booth.dto;

import jakarta.validation.constraints.NotBlank;

public class SelectTemplateRequest {

  @NotBlank
  private String templateId;

  public String getTemplateId() { return templateId; }
  public void setTemplateId(String templateId) { this.templateId = templateId; }
}
