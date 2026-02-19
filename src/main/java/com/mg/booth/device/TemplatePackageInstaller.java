package com.mg.booth.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mg.booth.config.BoothProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Template package installer
 * Handles download, checksum verification, extraction, and atomic installation
 */
@Service
public class TemplatePackageInstaller {
  private static final Logger log = LoggerFactory.getLogger(TemplatePackageInstaller.class);

  private final BoothProps props;
  private final LocalTemplateIndexStore indexStore;
  private final ObjectMapper objectMapper;
  
  // Concurrent installation locks: key = "templateCode:versionSemver"
  private final Map<String, Lock> installationLocks = new ConcurrentHashMap<>();

  public TemplatePackageInstaller(BoothProps props, LocalTemplateIndexStore indexStore) {
    this.props = props;
    this.indexStore = indexStore;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Install template package
   *
   * @param templateCode Template code (string, e.g. "tpl_001")
   * @param versionSemver Version string (semver format, e.g. "0.1.0")
   * @param downloadUrl Download URL
   * @param checksumSha256 Expected SHA256 checksum (with or without "sha256:" prefix)
   * @return Installed path (relative to dataDir)
   * @throws RuntimeException if installation fails
   */
  public String install(String templateCode, String versionSemver, String downloadUrl, String checksumSha256) {
    // Install dry-run: log parsed parameters
    log.info("[tpl-install] Install dry-run: templateCode={}, versionSemver={}, downloadUrl={}, checksumSha256={}", 
        templateCode, versionSemver, downloadUrl, 
        checksumSha256 != null && checksumSha256.length() > 16 
            ? checksumSha256.substring(0, 16) + "..." 
            : checksumSha256);
    
    // Normalize checksum (remove "sha256:" prefix if present)
    String normalizedChecksum = checksumSha256 != null && checksumSha256.startsWith("sha256:") 
        ? checksumSha256.substring(7) 
        : checksumSha256;
    
    // Get installation lock for this template+version
    String lockKey = templateCode + ":" + versionSemver;
    Lock lock = installationLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
    
    lock.lock();
    try {
      log.info("[tpl-install] Starting installation: templateCode={}, versionSemver={}, downloadUrl={}", 
          templateCode, versionSemver, downloadUrl);
      
      Path dataDir = getDataDir();
      Path tmpDir = dataDir.resolve("tmp");
      Path downloadsDir = tmpDir.resolve("downloads");
      Path stagingDir = tmpDir.resolve("staging");
      
      // Ensure directories exist
      Files.createDirectories(downloadsDir);
      Files.createDirectories(stagingDir);
      
      // Step 1: Download
      String uuid = UUID.randomUUID().toString();
      Path zipPartFile = downloadsDir.resolve(uuid + ".zip.part");
      Path zipFile = downloadsDir.resolve(uuid + ".zip");
      
      log.info("[tpl-install] Step=download: templateCode={}, versionSemver={}, url={}", 
          templateCode, versionSemver, downloadUrl);
      
      downloadFile(downloadUrl, zipPartFile);
      Files.move(zipPartFile, zipFile, StandardCopyOption.REPLACE_EXISTING);
      
      log.info("[tpl-install] Step=download-complete: templateCode={}, versionSemver={}, size={}", 
          templateCode, versionSemver, Files.size(zipFile));
      
      // Step 2: Verify checksum
      log.info("[tpl-install] Step=verify: templateCode={}, versionSemver={}", templateCode, versionSemver);
      
      String actualChecksum = calculateSha256(zipFile);
      if (!actualChecksum.equalsIgnoreCase(normalizedChecksum)) {
        Files.deleteIfExists(zipFile);
        throw new RuntimeException(
            String.format("Checksum mismatch: expected=%s, actual=%s", normalizedChecksum, actualChecksum));
      }
      
      log.info("[tpl-install] Step=verify-complete: templateCode={}, versionSemver={}, checksum={}", 
          templateCode, versionSemver, actualChecksum);
      
      // Step 3: Extract to staging
      Path stagingTemplateDir = stagingDir.resolve(templateCode + "/" + versionSemver + "_" + uuid);
      log.info("[tpl-install] Step=unzip: templateCode={}, versionSemver={}, staging={}", 
          templateCode, versionSemver, stagingTemplateDir);
      
      extractZip(zipFile, stagingTemplateDir);
      
      log.info("[tpl-install] Step=unzip-complete: templateCode={}, versionSemver={}", templateCode, versionSemver);
      
      // Step 4: Validate manifest.json
      log.info("[tpl-install] Step=validate-manifest: templateCode={}, versionSemver={}", templateCode, versionSemver);
      
      Path manifestFile = stagingTemplateDir.resolve("manifest.json");
      if (!Files.exists(manifestFile)) {
        cleanupStaging(stagingTemplateDir);
        throw new RuntimeException("manifest.json not found in template package");
      }
      
      validateManifest(manifestFile, templateCode, versionSemver);

      // Optional: validate required asset files exist (rules.json, preview.png)
      Path manifestDir = manifestFile.getParent();
      Path rulesFile = manifestDir.resolve("rules.json");
//      Path previewFile = manifestDir.resolve("preview.png");
//      Path previewFile2 = manifestDir.resolve("preview.jpg");
      // preview 可以是 preview.png 或 preview.jpg，二选一
        Path previewFilePng = manifestDir.resolve("preview.png");
        Path previewFileJpg = manifestDir.resolve("preview.jpg");
         Path previewFile = null;
        if (Files.exists(previewFilePng)) {
          previewFile = previewFilePng;
        } else if (Files.exists(previewFileJpg)) {
          previewFile = previewFileJpg;
        }
      if (!Files.exists(rulesFile) || !Files.exists(previewFile)) {
        cleanupStaging(stagingTemplateDir);
        throw new RuntimeException("Template assets missing: rules.json and/or preview.png not found");
      }
      
      log.info("[tpl-install] Step=validate-manifest-complete: templateCode={}, versionSemver={}", templateCode, versionSemver);
      
      // Step 5: Atomic commit to final directory
      Path finalDir = dataDir.resolve("templates").resolve(templateCode).resolve(versionSemver);
      log.info("[tpl-install] Step=commit: templateCode={}, versionSemver={}, finalDir={}", 
          templateCode, versionSemver, finalDir);

      // Ensure parent directory exists: data/templates/<templateCode>
      Files.createDirectories(finalDir.getParent());

      // Sanity check: staging directory must exist before commit
      if (!Files.exists(stagingTemplateDir)) {
        throw new RuntimeException("staging dir missing before commit: " + stagingTemplateDir);
      }

      // Backup existing directory if exists
      if (Files.exists(finalDir)) {
        Path backupDir = finalDir.resolveSibling(versionSemver + ".bak_" + System.currentTimeMillis());
        try {
          Files.move(finalDir, backupDir, StandardCopyOption.REPLACE_EXISTING);
          log.info("[tpl-install] Backed up existing directory: {}", backupDir);
          // Delete backup after successful move (optional, can keep for recovery)
        } catch (Exception e) {
          log.warn("[tpl-install] Failed to backup existing directory, will delete: {}", e.getMessage());
          deleteDirectory(finalDir);
        }
      }
      
      // Atomic move staging to final
      try {
        Files.move(stagingTemplateDir, finalDir, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (UnsupportedOperationException e) {
        log.debug("[tpl-install] ATOMIC_MOVE not supported, using REPLACE_EXISTING");
        Files.move(stagingTemplateDir, finalDir, StandardCopyOption.REPLACE_EXISTING);
      }
      
      log.info("[tpl-install] Step=commit-complete: templateCode={}, versionSemver={}, finalDir={}", 
          templateCode, versionSemver, finalDir);
      
      // Step 6: Update index.json
      log.info("[tpl-install] Step=update-index: templateCode={}, versionSemver={}", templateCode, versionSemver);
      
      Path indexFile = dataDir.resolve("index.json");
      LocalTemplateIndexStore.TemplateIndex index = indexStore.readIndex(indexFile)
          .orElse(new LocalTemplateIndexStore.TemplateIndex());
      
      // Remove existing entry for this templateCode+versionSemver
      index.getItems().removeIf(item ->
          templateCode.equals(item.getTemplateId()) && item.getVersion().equals(versionSemver));
      
      // Add new entry
      String relativePath = "templates/" + templateCode + "/" + versionSemver;
      LocalTemplateIndexStore.TemplateIndexItem newItem = new LocalTemplateIndexStore.TemplateIndexItem(
          templateCode, versionSemver, relativePath, Instant.now(), actualChecksum, downloadUrl);
      index.getItems().add(newItem);
      
      indexStore.writeIndex(indexFile, index);
      
      log.info("[tpl-install] Step=update-index-complete: templateCode={}, versionSemver={}", templateCode, versionSemver);
      
      // Step 7: Cleanup tmp files
      try {
        Files.deleteIfExists(zipFile);
      } catch (Exception e) {
        log.warn("[tpl-install] Failed to delete tmp zip file: {}", e.getMessage());
      }
      
      log.info("[tpl-install] Installation complete: templateCode={}, versionSemver={}, path={}", 
          templateCode, versionSemver, relativePath);
      
      return relativePath;
      
    } catch (Exception e) {
      log.error("[tpl-install] Installation failed: templateCode={}, versionSemver={}, error={}", 
          templateCode, versionSemver, e.getMessage(), e);
      throw new RuntimeException("Template installation failed: " + e.getMessage(), e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Get data directory path
   */
  private Path getDataDir() {
    String dataDirStr = props.getDataDir();
    if (dataDirStr == null || dataDirStr.isBlank()) {
      dataDirStr = "./data";
    }
    Path dataDir = Path.of(dataDirStr);
    
    // Ensure data directory and subdirectories exist
    try {
      Files.createDirectories(dataDir.resolve("templates"));
      Files.createDirectories(dataDir.resolve("tmp/downloads"));
      Files.createDirectories(dataDir.resolve("tmp/staging"));
    } catch (IOException e) {
      throw new RuntimeException("Failed to create data directory: " + dataDir, e);
    }
    
    return dataDir;
  }

  /**
   * Download file from URL
   */
  private void downloadFile(String url, Path targetFile) throws IOException {
    try (InputStream in = new URL(url).openStream()) {
      Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Calculate SHA256 checksum of file
   */
  private String calculateSha256(Path file) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new RuntimeException("Failed to get SHA-256 digest", e);
    }
    
    try (FileInputStream fis = new FileInputStream(file.toFile())) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = fis.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
      }
    }
    
    byte[] hash = digest.digest();
    StringBuilder hexString = new StringBuilder();
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /**
   * Extract ZIP file to target directory
   */
  private void extractZip(Path zipFile, Path targetDir) throws IOException {
    Files.createDirectories(targetDir);
    
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        Path entryPath = targetDir.resolve(entry.getName());
        
        // Security: prevent zip slip
        if (!entryPath.normalize().startsWith(targetDir.normalize())) {
          throw new IOException("Invalid zip entry: " + entry.getName());
        }
        
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          Files.createDirectories(entryPath.getParent());
          Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }
  }

  /**
   * Validate manifest.json
   * Validates that manifest.templateId equals templateCode (string comparison)
   * and manifest.version equals versionSemver
   */
  private void validateManifest(Path manifestFile, String expectedTemplateCode, String expectedVersionSemver) throws IOException {
    String json = Files.readString(manifestFile);
    JsonNode manifest = objectMapper.readTree(json);
    
    // Check templateId (must equal templateCode as string)
    JsonNode templateIdNode = manifest.get("templateId");  //  这个应该弃用了
    JsonNode templateCodeNode = manifest.get("templateCode");  // 应该是这个
    if (templateIdNode == null && templateCodeNode == null) {   // 都是null
      throw new RuntimeException("manifest.json missing templateId field");
    }
//    String actualTemplateId = templateIdNode.asText();
    String actualTemplateId = templateCodeNode != null ? templateCodeNode.asText() : templateIdNode.asText();
    if (!expectedTemplateCode.equals(actualTemplateId)) {
      throw new RuntimeException(
          String.format("manifest.json templateId mismatch: expected=%s, actual=%s",
              expectedTemplateCode, actualTemplateId));
    }
    
    // Check version (must equal versionSemver)
//    JsonNode versionNode = manifest.get("version");
   // now is  versionSemver xj 2026-2-18
        JsonNode versionNode = manifest.get("versionSemver");
    if (versionNode == null) {
      throw new RuntimeException("manifest.json missing version field");
    }
    String actualVersion = versionNode.asText();
    if (!actualVersion.equals(expectedVersionSemver)) {
      throw new RuntimeException(
          String.format("manifest.json version mismatch: expected=%s, actual=%s", 
              expectedVersionSemver, actualVersion));
    }
  }

  /**
   * Cleanup staging directory
   */
  private void cleanupStaging(Path stagingDir) {
    try {
      deleteDirectory(stagingDir);
    } catch (Exception e) {
      log.warn("[tpl-install] Failed to cleanup staging directory: {}", e.getMessage());
    }
  }

  /**
   * Delete directory recursively
   */
  private void deleteDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      Files.walk(dir)
          .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              log.warn("[tpl-install] Failed to delete: {}", path);
            }
          });
    }
  }
}
