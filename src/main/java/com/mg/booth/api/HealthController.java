package com.mg.booth.api;

import com.mg.booth.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse(true, "1.0.0", OffsetDateTime.now());
  }
}
