package com.mg.booth.scheduler;

import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionState;
import com.mg.booth.service.DeliveryService;
import com.mg.booth.service.IdempotencyService;
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
  private final IdempotencyService idempotencyService;

  public SessionSweeper(SessionService sessionService, DeliveryService deliveryService, IdempotencyService idempotencyService) {
    this.sessionService = sessionService;
    this.deliveryService = deliveryService;
    this.idempotencyService = idempotencyService;
  }

  @Scheduled(fixedDelay = 1000)
  public void sweepTimeouts() {
    Map<String, Session> store = sessionService.unsafeStore();
    OffsetDateTime now = OffsetDateTime.now();

    for (Session s : store.values()) {
      if (s.getStateEnteredAt() == null) continue;
      long seconds = Duration.between(s.getStateEnteredAt(), now).toSeconds();

      // SELECTING 30s -> IDLE
      if (s.getState() == SessionState.SELECTING && seconds > 30) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "TIMEOUT_SELECTING");
        }
        continue;
      }

      // LIVE_PREVIEW 30s -> IDLE（Phase 4: 取景超时回收）
      if (s.getState() == SessionState.LIVE_PREVIEW && seconds > 30) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "TIMEOUT_LIVE_PREVIEW");
        }
        continue;
      }

      // COUNTDOWN 兜底：15s -> IDLE
      if (s.getState() == SessionState.COUNTDOWN && seconds > 15) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "TIMEOUT_COUNTDOWN");
        }
        continue;
      }

      // PROCESSING 30s -> IDLE（Day7 要求更敏捷的回收）
      if (s.getState() == SessionState.PROCESSING && seconds > 30) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "TIMEOUT_PROCESSING");
        }
        continue;
      }

      // PREVIEW 30s -> IDLE
      if (s.getState() == SessionState.PREVIEW && seconds > 30) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "TIMEOUT_PREVIEW");
        }
        continue;
      }

      // DELIVERING 30s -> IDLE
      if (s.getState() == SessionState.DELIVERING && seconds > 30) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "TIMEOUT_DELIVERING");
        }
        continue;
      }

      // DONE 5s -> IDLE（快速回收）
      if (s.getState() == SessionState.DONE && seconds > 5) {
        synchronized (s) {
          safeFinish(s.getSessionId(), "AUTO_RECYCLE_DONE");
        }
      }
    }

    // 清理过期 token 和幂等缓存
    deliveryService.cleanupExpired();
    idempotencyService.cleanupExpired();
  }

  private void safeFinish(String sessionId, String reason) {
    try {
      sessionService.finish(sessionId, reason);
    } catch (Exception ignored) {
      // finish 自己可能因为状态机限制失败；Day7 应尽量避免这种情况
    }
  }
}
