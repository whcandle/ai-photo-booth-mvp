package com.mg.booth.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class StorageService {

  private final Path storageRoot = Path.of("storage");

  // Raw image storage
  public Path rawDir(String sessionId) {
    return storageRoot.resolve("raw").resolve(sessionId);
  }

  public Path rawFilePath(String sessionId, int attemptIndex) {
    return rawDir(sessionId).resolve(attemptIndex + ".jpg");
  }

  public void ensureDir(Path dir) {
    try {
      Files.createDirectories(dir);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create dir: " + dir, e);
    }
  }

  public String rawUrl(String sessionId, int attemptIndex) {
    return "/files/raw/" + sessionId + "/" + attemptIndex + ".jpg";
  }

  public Path previewDir(String sessionId) {
    return storageRoot.resolve("preview").resolve(sessionId);
  }

  public Path finalDir(String sessionId) {
    return storageRoot.resolve("final").resolve(sessionId);
  }

  public Path previewFilePath(String sessionId, int attemptIndex) {
    return previewDir(sessionId).resolve(attemptIndex + ".jpg");
  }

  public Path finalFilePath(String sessionId, int attemptIndex) {
    return finalDir(sessionId).resolve(attemptIndex + ".jpg");
  }

  public String previewUrl(String sessionId, int attemptIndex) {
    return "/files/preview/" + sessionId + "/" + attemptIndex + ".jpg";
  }

  public String finalUrl(String sessionId, int attemptIndex) {
    return "/files/final/" + sessionId + "/" + attemptIndex + ".jpg";
  }
}

