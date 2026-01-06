package com.mg.booth.domain;

public enum SessionState {
  IDLE,   // waiting for user to start
  SELECTING,  // user is selecting options
  COUNTDOWN,  // countdown before capture
  CAPTURING,  // capturing photos
  PROCESSING,   // processing photos
  PREVIEW,  // user is previewing photos
  DELIVERING,  // delivering photos
  DONE,   // session completed successfully
  ERROR  // error occurred during session
}
