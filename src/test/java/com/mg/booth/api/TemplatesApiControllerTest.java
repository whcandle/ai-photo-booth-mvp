package com.mg.booth.api;

import com.mg.booth.config.BoothProps;
import com.mg.booth.device.LocalTemplateIndexStore;
import com.mg.booth.domain.TemplateSummary;
import com.mg.booth.service.TemplateService;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TemplatesApiController
 */
@ExtendWith(MockitoExtension.class)
class TemplatesApiControllerTest {

  @Mock
  private BoothProps props;

  @Mock
  private LocalTemplateIndexStore indexStore;

  @Mock
  private TemplateService templateService;

  @InjectMocks
  private TemplatesApiController controller;

  @BeforeEach
  void setUp() {
    when(props.getDataDir()).thenReturn("./data");
  }

  @Test
  void testListTemplates_FromLocalIndex() {
    // Arrange: Mock indexStore returns non-empty index
    LocalTemplateIndexStore.TemplateIndex index = new LocalTemplateIndexStore.TemplateIndex();
    List<LocalTemplateIndexStore.TemplateIndexItem> items = new ArrayList<>();
    
    LocalTemplateIndexStore.TemplateIndexItem item1 = new LocalTemplateIndexStore.TemplateIndexItem();
    item1.setTemplateId("tpl_001");
    item1.setVersion("0.1.1");
    item1.setPath("templates/tpl_001/0.1.1");
    item1.setInstalledAt(Instant.now());
    items.add(item1);
    
    LocalTemplateIndexStore.TemplateIndexItem item2 = new LocalTemplateIndexStore.TemplateIndexItem();
    item2.setTemplateId("tpl_002");
    item2.setVersion("0.1.2");
    item2.setPath("templates/tpl_002/0.1.2");
    item2.setInstalledAt(Instant.now());
    items.add(item2);
    
    index.setItems(items);
    
    when(indexStore.readIndex(any(Path.class))).thenReturn(Optional.of(index));

    // Act
    Map<String, Object> response = controller.listTemplates();

    // Assert
    assertNotNull(response);
    assertTrue(response.containsKey("items"));
    
    @SuppressWarnings("unchecked")
    List<TemplatesApiController.TemplateItemDto> resultItems = 
        (List<TemplatesApiController.TemplateItemDto>) response.get("items");
    
    assertNotNull(resultItems);
    assertEquals(2, resultItems.size());
    
    TemplatesApiController.TemplateItemDto dto1 = resultItems.get(0);
    assertEquals("tpl_001", dto1.getTemplateId());
    assertEquals("tpl_001", dto1.getName());
    assertTrue(dto1.isEnabled());
    
    TemplatesApiController.TemplateItemDto dto2 = resultItems.get(1);
    assertEquals("tpl_002", dto2.getTemplateId());
    assertEquals("tpl_002", dto2.getName());
    assertTrue(dto2.isEnabled());

    // Verify templateService was NOT called (local index was used)
    verify(templateService, never()).listTemplates();
  }

  @Test
  void testListTemplates_FallbackToService_WhenIndexEmpty() {
    // Arrange: Mock indexStore returns empty index
    when(indexStore.readIndex(any(Path.class))).thenReturn(Optional.empty());

    // Mock templateService returns hardcoded templates
    List<TemplateSummary> summaries = List.of(
        new TemplateSummary("tpl_001", "NewYear", true),
        new TemplateSummary("tpl_002", "SpringFest", true),
        new TemplateSummary("tpl_005", "FunFrame", false)
    );
    when(templateService.listTemplates()).thenReturn(summaries);

    // Act
    Map<String, Object> response = controller.listTemplates();

    // Assert
    assertNotNull(response);
    assertTrue(response.containsKey("items"));
    
    @SuppressWarnings("unchecked")
    List<TemplatesApiController.TemplateItemDto> resultItems = 
        (List<TemplatesApiController.TemplateItemDto>) response.get("items");
    
    assertNotNull(resultItems);
    assertEquals(3, resultItems.size());
    
    TemplatesApiController.TemplateItemDto dto1 = resultItems.get(0);
    assertEquals("tpl_001", dto1.getTemplateId());
    assertEquals("NewYear", dto1.getName());
    assertTrue(dto1.isEnabled());
    
    TemplatesApiController.TemplateItemDto dto2 = resultItems.get(1);
    assertEquals("tpl_002", dto2.getTemplateId());
    assertEquals("SpringFest", dto2.getName());
    assertTrue(dto2.isEnabled());
    
    TemplatesApiController.TemplateItemDto dto3 = resultItems.get(2);
    assertEquals("tpl_005", dto3.getTemplateId());
    assertEquals("FunFrame", dto3.getName());
    assertFalse(dto3.isEnabled());

    // Verify templateService was called (fallback)
    verify(templateService, times(1)).listTemplates();
  }

  @Test
  void testListTemplates_FallbackToService_WhenIndexHasEmptyItems() {
    // Arrange: Mock indexStore returns index with empty items list
    LocalTemplateIndexStore.TemplateIndex index = new LocalTemplateIndexStore.TemplateIndex();
    index.setItems(new ArrayList<>()); // Empty list
    
    when(indexStore.readIndex(any(Path.class))).thenReturn(Optional.of(index));

    // Mock templateService returns hardcoded templates
    List<TemplateSummary> summaries = List.of(
        new TemplateSummary("tpl_001", "NewYear", true)
    );
    when(templateService.listTemplates()).thenReturn(summaries);

    // Act
    Map<String, Object> response = controller.listTemplates();

    // Assert
    assertNotNull(response);
    assertTrue(response.containsKey("items"));
    
    @SuppressWarnings("unchecked")
    List<TemplatesApiController.TemplateItemDto> resultItems = 
        (List<TemplatesApiController.TemplateItemDto>) response.get("items");
    
    assertNotNull(resultItems);
    assertEquals(1, resultItems.size()); // From fallback service
    
    TemplatesApiController.TemplateItemDto dto = resultItems.get(0);
    assertEquals("tpl_001", dto.getTemplateId());
    assertEquals("NewYear", dto.getName());
    assertTrue(dto.isEnabled());

    // Verify templateService was called (fallback)
    verify(templateService, times(1)).listTemplates();
  }

  @Test
  void testListTemplates_FailOpen_WhenIndexReadFails() {
    // Arrange: Mock indexStore throws exception
    when(indexStore.readIndex(any(Path.class))).thenThrow(new RuntimeException("File read error"));

    // Mock templateService returns hardcoded templates
    List<TemplateSummary> summaries = List.of(
        new TemplateSummary("tpl_001", "NewYear", true)
    );
    when(templateService.listTemplates()).thenReturn(summaries);

    // Act
    Map<String, Object> response = controller.listTemplates();

    // Assert: Should still return items from fallback
    assertNotNull(response);
    assertTrue(response.containsKey("items"));
    
    @SuppressWarnings("unchecked")
    List<TemplatesApiController.TemplateItemDto> resultItems = 
        (List<TemplatesApiController.TemplateItemDto>) response.get("items");
    
    assertNotNull(resultItems);
    assertEquals(1, resultItems.size()); // From fallback service

    // Verify templateService was called (fail-open fallback)
    verify(templateService, times(1)).listTemplates();
  }
}
