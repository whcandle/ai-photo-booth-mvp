package com.mg.booth.api;

import com.mg.booth.dto.TemplateListResponse;
import com.mg.booth.service.TemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/templates")
    public TemplateListResponse list() {
        return new TemplateListResponse(templateService.listTemplates());
    }
}
