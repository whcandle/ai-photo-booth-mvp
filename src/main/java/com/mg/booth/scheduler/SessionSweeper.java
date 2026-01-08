package com.mg.booth.scheduler;

import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionProgress;
import com.mg.booth.domain.SessionState;
import com.mg.booth.dto.ApiError;
import com.mg.booth.service.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class SessionSweeper {

  private final SessionService sessionService;

  public SessionSweeper(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @Scheduled(fixedDelay = 1000)
  public void sweepTimeouts() {
    Map<String, Session> store = sessionService.unsafeStore();
    OffsetDateTime now = OffsetDateTime.now();

    for (Session s : store.values()) {
      if (s.getStateEnteredAt() == null) continue;

      if (s.getState() == SessionState.PROCESSING) {
        long seconds = Duration.between(s.getStateEnteredAt(), now).toSeconds();
        if (seconds > 30) {
          synchronized (s) {
            s.setError(new ApiError("TIMEOUT", "Processing timeout", Map.of("state", "PROCESSING", "seconds", seconds)));
            s.setState(SessionState.ERROR);
            s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "处理超时，返回首页", 0));
            s.setUpdatedAt(now);
            s.setStateEnteredAt(now);

            // 立刻回到 IDLE（MVP 兜底）
            s.setState(SessionState.IDLE);
            s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "已回到首页", 0));
            s.setUpdatedAt(now);
            s.setStateEnteredAt(now);

            s.setCaptureJobRunning(false);
            s.setAiJobRunning(false);
          }
        }
      }
    }
  }
}
