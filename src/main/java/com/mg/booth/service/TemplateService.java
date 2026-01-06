package com.mg.booth.service;

import com.mg.booth.domain.TemplateSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateService {
  public List<TemplateSummary> listTemplates() {
    return List.of(
      new TemplateSummary("tpl_001", "NewYear", true),
      new TemplateSummary("tpl_002", "SpringFest", true),
      new TemplateSummary("tpl_003", "BrandDay", true),
      new TemplateSummary("tpl_004", "Minimal", true),
      new TemplateSummary("tpl_005", "FunFrame", false)
    );
  }
}
