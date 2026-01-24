package com.mg.booth.service;

import com.mg.booth.camera.CameraService;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class MockCameraService implements CameraService {

  private final Random random = new Random();
  private final Path sampleDir = Path.of("assets", "sample_raw");

  public void captureTo(Path targetFile) {
    try {
      // 0.5~1s delay
      long delayMs = 500 + random.nextInt(501);
      Thread.sleep(delayMs);

      if (!Files.exists(sampleDir) || !Files.isDirectory(sampleDir)) {
        throw new IllegalStateException("Missing sample directory: " + sampleDir.toAbsolutePath());
      }

      List<Path> samples = Files.list(sampleDir)
        .filter(p -> !Files.isDirectory(p))
        .filter(p -> {
          String name = p.getFileName().toString().toLowerCase();
          return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
        })
        .collect(Collectors.toList());

      if (samples.isEmpty()) {
        throw new IllegalStateException("No sample images in: " + sampleDir.toAbsolutePath());
      }

      Path src = samples.get(random.nextInt(samples.size()));
      Files.copy(src, targetFile, StandardCopyOption.REPLACE_EXISTING);

    } catch (Exception e) {
      throw new RuntimeException("Mock camera capture failed", e);
    }
  }

  @Override
  public CameraStatus getStatus() throws Exception {
    return null;
  }
}

