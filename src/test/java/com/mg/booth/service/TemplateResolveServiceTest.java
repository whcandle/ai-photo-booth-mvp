package com.mg.booth.service;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.LocalTemplateIndexStore;
import com.mg.booth.domain.TemplateSummary;
import com.mg.booth.domain.V2TemplateRef;
import com.mg.booth.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TemplateResolveService.
 */
@ExtendWith(MockitoExtension.class)
class TemplateResolveServiceTest {

  @Mock
  private BoothProps props;

  @Mock
  private LocalTemplateIndexStore indexStore;

  @Mock
  private TemplateService templateService;

  @InjectMocks
  private TemplateResolveService service;

  @BeforeEach
  void setUp() {
    // use real default dataDir behavior; no stubbing to avoid unnecessary stubbing warning
  }

  @Test
  void resolveForV2_shouldReturnRef_whenTemplateExistsAndInstalled() {
    // Arrange
    String templateId = "tpl_001";

    // TemplateService: template exists and enabled
    List<TemplateSummary> summaries = List.of(
        new TemplateSummary("tpl_001", "NewYear", true),
        new TemplateSummary("tpl_002", "SpringFest", true)
    );
    when(templateService.listTemplates()).thenReturn(summaries);

    // LocalTemplateIndexStore: index contains matching item
    LocalTemplateIndexStore.TemplateIndex index = new LocalTemplateIndexStore.TemplateIndex();
    var items = new ArrayList<LocalTemplateIndexStore.TemplateIndexItem>();

    LocalTemplateIndexStore.TemplateIndexItem item = new LocalTemplateIndexStore.TemplateIndexItem();
    item.setTemplateId("tpl_001");
    item.setVersion("0.2.0");
    item.setPath("templates/tpl_001/0.2.0");
    item.setInstalledAt(Instant.now());
    item.setDownloadUrl("http://localhost/templates/tpl_001-0.2.0.zip");
    item.setChecksum("abc123");
    items.add(item);

    index.setItems(items);

    when(indexStore.readIndex(any(Path.class))).thenReturn(Optional.of(index));

    // Act
    V2TemplateRef ref = service.resolveForV2(templateId);

    // Assert
    assertNotNull(ref);
    assertEquals("tpl_001", ref.getTemplateCode());
    assertEquals("0.2.0", ref.getVersionSemver());
    assertEquals("http://localhost/templates/tpl_001-0.2.0.zip", ref.getDownloadUrl());
    assertEquals("abc123", ref.getChecksumSha256());
  }

  @Test
  void resolveForV2_shouldThrowInvalidInput_whenTemplateNotFoundOrDisabledInService() {
    // Arrange: TemplateService does not contain enabled template for given id
    String templateId = "tpl_999";

    List<TemplateSummary> summaries = List.of(
        new TemplateSummary("tpl_001", "NewYear", true),
        new TemplateSummary("tpl_002", "SpringFest", false) // disabled
    );
    when(templateService.listTemplates()).thenReturn(summaries);

    // Act + Assert
    ApiException ex = assertThrows(ApiException.class, () -> service.resolveForV2(templateId));
    assertEquals("INVALID_INPUT", ex.getCode());
  }

  @Test
  void resolveForV2_shouldThrowInvalidInput_whenTemplateNotInstalledInIndex() {
    // Arrange
    String templateId = "tpl_001";

    // TemplateService: template exists and enabled
    List<TemplateSummary> summaries = List.of(
        new TemplateSummary("tpl_001", "NewYear", true)
    );
    when(templateService.listTemplates()).thenReturn(summaries);

    // LocalTemplateIndexStore: index exists but does NOT contain the template
    LocalTemplateIndexStore.TemplateIndex index = new LocalTemplateIndexStore.TemplateIndex();
    var items = new ArrayList<LocalTemplateIndexStore.TemplateIndexItem>();

    LocalTemplateIndexStore.TemplateIndexItem item = new LocalTemplateIndexStore.TemplateIndexItem();
    item.setTemplateId("tpl_002");
    item.setVersion("0.1.0");
    item.setPath("templates/tpl_002/0.1.0");
    item.setInstalledAt(Instant.now());
    items.add(item);

    index.setItems(items);

    when(indexStore.readIndex(any(Path.class))).thenReturn(Optional.of(index));

    // Act + Assert
    ApiException ex = assertThrows(ApiException.class, () -> service.resolveForV2(templateId));
    assertEquals("INVALID_INPUT", ex.getCode());
  }
}

