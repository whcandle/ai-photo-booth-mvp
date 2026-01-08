package com.mg.booth.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;

@Service
public class MockAiService {

  private final Random random = new Random();

  /**
   * Mock AI: 3~8s. Generates preview & final.
   * For MVP: simply copy raw -> preview, raw -> final.
   */
  public void process(Path rawFile, Path previewFile, Path finalFile) {
    try {
      // total delay 3~8s
      int totalMs = 3000 + random.nextInt(5001);

      // split into chunks to allow progress updates in SessionService
      // (actual sleeping is done outside; here just do file ops)
      Thread.sleep(totalMs);

      Files.copy(rawFile, previewFile, StandardCopyOption.REPLACE_EXISTING);
      Files.copy(rawFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new RuntimeException("Mock AI processing failed", e);
    }
  }
}
