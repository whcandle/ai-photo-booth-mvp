package com.mg.booth.service;

import com.mg.booth.domain.SessionState;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class SessionStateMachine {

  private final Map<SessionState, Set<SessionState>> allowed = new EnumMap<>(SessionState.class);

  public SessionStateMachine() {
    // CreateSession: (virtual) IDLE -> SELECTING
    allowed.put(SessionState.IDLE, EnumSet.of(SessionState.SELECTING));

    // SelectTemplate: SELECTING -> LIVE_PREVIEW (策略2: 一起改，直接走取景)
    // Day7: 允许 SELECTING -> IDLE（取消/超时回收）
    allowed.put(SessionState.SELECTING, EnumSet.of(SessionState.LIVE_PREVIEW, SessionState.IDLE));

    // Live preview -> countdown
    allowed.put(SessionState.LIVE_PREVIEW, EnumSet.of(SessionState.COUNTDOWN, SessionState.IDLE));

    // Capture: COUNTDOWN -> CAPTURING
    // 允许 COUNTDOWN -> IDLE：取消/超时回收
    allowed.put(SessionState.COUNTDOWN, EnumSet.of(SessionState.CAPTURING, SessionState.IDLE));

    // Camera done -> PROCESSING (Day4 will continue)
    // 允许 CAPTURING -> IDLE：兜底回收
    allowed.put(SessionState.CAPTURING, EnumSet.of(SessionState.PROCESSING, SessionState.IDLE));

    // Finish: allow recovery to IDLE
    // 允许 PROCESSING -> IDLE：超时/失败回收
    allowed.put(SessionState.PROCESSING, EnumSet.of(SessionState.PREVIEW, SessionState.IDLE));
    allowed.put(SessionState.PREVIEW, EnumSet.of(SessionState.COUNTDOWN, SessionState.DELIVERING, SessionState.IDLE));
    allowed.put(SessionState.DELIVERING, EnumSet.of(SessionState.DONE, SessionState.IDLE));
    allowed.put(SessionState.DONE, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.ERROR, EnumSet.of(SessionState.IDLE));
  }

  public boolean canTransition(SessionState from, SessionState to) {
    return allowed.getOrDefault(from, Set.of()).contains(to);
  }
}
