package com.mg.booth.service;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.LocalTemplateIndexStore;
import com.mg.booth.domain.TemplateSummary;
import com.mg.booth.domain.V2TemplateRef;
import com.mg.booth.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Resolve v2 template metadata from local index.json based on session.templateId.
 *
 * Requirements:
 * - Offline only (no platform dependency)
 * - Validate template exists and enabled (via TemplateService)
 * - Read metadata from LocalTemplateIndexStore (data/index.json)
 * - Throw INVALID_INPUT if template not found or disabled
 */
@Service
public class TemplateResolveService {

  private static final Logger log = LoggerFactory.getLogger(TemplateResolveService.class);

  private final BoothProps props;
  private final LocalTemplateIndexStore indexStore;
  private final TemplateService templateService;

  public TemplateResolveService(
      BoothProps props,
      LocalTemplateIndexStore indexStore,
      TemplateService templateService
  ) {
    this.props = props;
    this.indexStore = indexStore;
    this.templateService = templateService;
  }

  /**
   * Resolve v2 template metadata for given templateId.
   *
   * @param templateId session.templateId (e.g. "tpl_002")
   * @return V2TemplateRef with templateCode, versionSemver, downloadUrl, checksumSha256
   * @throws ApiException INVALID_INPUT if template not found, disabled or not installed
   */
  public V2TemplateRef resolveForV2(String templateId) {
    if (templateId == null || templateId.isBlank()) {
      throw new ApiException("INVALID_INPUT", "templateId is required", HttpStatus.BAD_REQUEST);
    }

    try {
      // 1) Resolve index.json path (for logging + diagnostics)
      Path dataDir = Path.of(props.getDataDir() != null && !props.getDataDir().isBlank()
          ? props.getDataDir()
          : "./data");
      Path indexFile = dataDir.resolve("index.json");

      var indexOpt = indexStore.readIndex(indexFile);
      int installedCount = indexOpt.isPresent() && indexOpt.get().getItems() != null
          ? indexOpt.get().getItems().size()
          : 0;

      // 2) Validate template exists and enabled using TemplateService (offline, hardcoded)
      boolean enabled = templateService.listTemplates().stream()
          .anyMatch(t -> t.isEnabled() && templateId.equals(t.getTemplateId()));

      if (!enabled) {
        String reason = String.format(
            "template not found or disabled: templateId=%s, index=%s, installedCount=%d",
            templateId,
            indexFile.toAbsolutePath(),
            installedCount
        );
        log.warn("[template-resolve] {}", reason);
        throw new ApiException("INVALID_INPUT", reason, HttpStatus.BAD_REQUEST);
      }

      if (indexOpt.isEmpty() || indexOpt.get().getItems() == null || indexOpt.get().getItems().isEmpty()) {
        String reason = String.format(
            "template not found or disabled: templateId=%s, index=%s, installedCount=%d",
            templateId,
            indexFile.toAbsolutePath(),
            installedCount
        );
        log.warn("[template-resolve] {}", reason);
        throw new ApiException("INVALID_INPUT", reason, HttpStatus.BAD_REQUEST);
      }

      LocalTemplateIndexStore.TemplateIndex index = indexOpt.get();

      // 3) Find matching item by templateId (schemaVersion 2: templateId stores templateCode)
      LocalTemplateIndexStore.TemplateIndexItem item = index.getItems().stream()
          .filter(it -> templateId.equals(it.getTemplateId()))
          .findFirst()
          .orElse(null);

      if (item == null) {
        String reason = String.format(
            "template not found or disabled: templateId=%s, index=%s, installedCount=%d",
            templateId,
            indexFile.toAbsolutePath(),
            installedCount
        );
        log.warn("[template-resolve] {}", reason);
        throw new ApiException("INVALID_INPUT", reason, HttpStatus.BAD_REQUEST);
      }

      String templateCode = item.getTemplateId();
      String versionSemver = item.getVersion();
      String downloadUrl = item.getDownloadUrl();
      String checksumSha256 = item.getChecksum();

      log.info("[template-resolve] templateId={} -> {}@{}, downloadUrl={}, indexFile={}",
          templateId, templateCode, versionSemver, downloadUrl, indexFile.toAbsolutePath());

      return new V2TemplateRef(templateCode, versionSemver, downloadUrl, checksumSha256);

    } catch (ApiException e) {
      // 业务异常直接抛出
      throw e;
    } catch (Exception e) {
      // 其他异常包装为业务异常，reason 使用真实异常信息
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn("[template-resolve] Failed to resolve templateId {}: {}", templateId, msg, e);
      String reason = String.format("template not found or disabled: templateId=%s, detail=%s",
          templateId, msg);
      throw new ApiException("INVALID_INPUT", reason, HttpStatus.BAD_REQUEST);
    }
  }
}

