package com.mg.booth.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class StorageService {

  private final Path storageRoot = Path.of("storage");

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
}

