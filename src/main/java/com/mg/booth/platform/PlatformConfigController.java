package com.mg.booth.platform;

import com.mg.booth.platform.dto.DeviceActivityDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlatformConfigController {

  private final PlatformSyncService sync;

  public PlatformConfigController(PlatformSyncService sync) {
    this.sync = sync;
  }

  @GetMapping("/api/v1/platform/activities")
  public List<DeviceActivityDto> activities() {
    return sync.getCachedActivities();
  }
}
