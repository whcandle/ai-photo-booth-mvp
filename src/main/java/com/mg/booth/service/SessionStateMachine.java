package com.mg.booth.service;

import com.mg.booth.domain.SessionState;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Central place to define allowed transitions.
 * Day2 only uses a small subset; Day3+ will extend here.
 */
@Component
public class SessionStateMachine {

  private final Map<SessionState, Set<SessionState>> allowed = new EnumMap<>(SessionState.class);

  public SessionStateMachine() {
    // CreateSession: (virtual) IDLE -> SELECTING
    allowed.put(SessionState.IDLE, EnumSet.of(SessionState.SELECTING));

    // SelectTemplate: SELECTING -> COUNTDOWN
    allowed.put(SessionState.SELECTING, EnumSet.of(SessionState.COUNTDOWN));

    // Finish: allow recovery from many states to IDLE (MVP needs "always recover")
    allowed.put(SessionState.COUNTDOWN, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.CAPTURING, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.PROCESSING, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.PREVIEW, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.DELIVERING, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.DONE, EnumSet.of(SessionState.IDLE));
    allowed.put(SessionState.ERROR, EnumSet.of(SessionState.IDLE));
  }

  public boolean canTransition(SessionState from, SessionState to) {
    return allowed.getOrDefault(from, Set.of()).contains(to);
  }

  public void assertCanTransition(SessionState from, SessionState to) {
    if (!canTransition(from, to)) {
      throw new IllegalStateException("Transition not allowed: " + from + " -> " + to);
    }
  }
}
