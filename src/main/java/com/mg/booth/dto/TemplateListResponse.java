package com.mg.booth.dto;

import com.mg.booth.domain.TemplateSummary;
import java.util.List;

public class TemplateListResponse {
  private List<TemplateSummary> items;

  public TemplateListResponse(List<TemplateSummary> items) {
    this.items = items;
  }

  public List<TemplateSummary> getItems() { return items; }
}
