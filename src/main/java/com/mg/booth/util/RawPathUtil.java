package com.mg.booth.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RawPathUtil {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

    private RawPathUtil() {}

    /**
     * 输出：{rawBaseDir}/sess_{sessionId}/IMG_{timestamp}.jpg
     */
    public static Path buildTargetFile(String rawBaseDir, String sessionId) throws Exception {
        Path sessionDir = Paths.get(rawBaseDir, "sess_" + sessionId);
        // 建不建都行（CameraAgent 也会建），但 MVP 建了更直观
        Files.createDirectories(sessionDir);

        String filename = "IMG_" + LocalDateTime.now().format(TS) + ".jpg";
        return sessionDir.resolve(filename);
    }
}
