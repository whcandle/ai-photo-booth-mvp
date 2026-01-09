package com.mg.booth.scheduler;

import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionProgress;
import com.mg.booth.domain.SessionState;
import com.mg.booth.dto.ApiError;
import com.mg.booth.service.DeliveryService;
import com.mg.booth.service.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class SessionSweeper {

  private final SessionService sessionService;
  private final DeliveryService deliveryService;

  public SessionSweeper(SessionService sessionService, DeliveryService deliveryService) {
    this.sessionService = sessionService;
    this.deliveryService = deliveryService;
  }

  @Scheduled(fixedDelay = 1000)
  public void sweepTimeouts() {
    Map<String, Session> store = sessionService.unsafeStore();
    OffsetDateTime now = OffsetDateTime.now();

    for (Session s : store.values()) {
      if (s.getStateEnteredAt() == null) continue;

      long seconds = Duration.between(s.getStateEnteredAt(), now).toSeconds();

      // PROCESSING 30s -> ERROR -> IDLE
      if (s.getState() == SessionState.PROCESSING && seconds > 30) {
        synchronized (s) {
          s.setError(new ApiError("TIMEOUT", "Processing timeout",
            Map.of("state", "PROCESSING", "seconds", seconds)));
          s.setState(SessionState.ERROR);
          s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "处理超时，返回首页", 0));
          s.setUpdatedAt(now);
          s.setStateEnteredAt(now);

          // 回 IDLE
          s.setState(SessionState.IDLE);
          s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "已回到首页", 0));
          s.setUpdatedAt(now);
          s.setStateEnteredAt(now);

          s.setCaptureJobRunning(false);
          s.setAiJobRunning(false);
          s.setDownloadToken(null);
          s.setDownloadUrl(null);
        }
        continue;
      }

      // PREVIEW 30s -> IDLE
      if (s.getState() == SessionState.PREVIEW && seconds > 30) {
        synchronized (s) {
          s.setState(SessionState.IDLE);
          s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "预览超时，已回到首页", 0));
          s.setUpdatedAt(now);
          s.setStateEnteredAt(now);
          s.setDownloadToken(null);
          s.setDownloadUrl(null);
        }
        continue;
      }

      // DELIVERING 30s -> DONE -> IDLE（你也可以直接 IDLE）
      if (s.getState() == SessionState.DELIVERING && seconds > 30) {
        synchronized (s) {
          s.setState(SessionState.DONE);
          s.setProgress(new SessionProgress(SessionProgress.Step.DELIVERY_READY, "已完成", 100));
          s.setUpdatedAt(now);
          s.setStateEnteredAt(now);

          s.setState(SessionState.IDLE);
          s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "已回到首页", 0));
          s.setUpdatedAt(now);
          s.setStateEnteredAt(now);
        }
        continue;
      }

      // DONE 若还停留也回收（保险）
      if (s.getState() == SessionState.DONE && seconds > 5) {
        synchronized (s) {
          s.setState(SessionState.IDLE);
          s.setProgress(new SessionProgress(SessionProgress.Step.NONE, "已回到首页", 0));
          s.setUpdatedAt(now);
          s.setStateEnteredAt(now);
        }
      }
    }

    deliveryService.cleanupExpired();
  }
}
