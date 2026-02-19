package com.mg.booth.service;

import com.mg.booth.config.BoothProps;
import com.mg.booth.domain.Session;
import com.mg.booth.domain.SessionState;
import com.mg.booth.domain.V2TemplateRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style test for AiProcessV2Service using mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class AiProcessV2ServiceTest {

  @Mock
  private TemplateResolveService templateResolveService;

  @Mock
  private AiGatewayV2Client aiGatewayV2Client;

  @Mock
  private BoothProps boothProps;

  @InjectMocks
  private AiProcessV2Service service;

  @Test
  void process_shouldWritePreviewAndFinalUrls_whenGatewayReturnsOk() {
    // Arrange
    Session session = new Session();
    session.setSessionId("sess_001");
    session.setTemplateId("tpl_001");
    session.setAttemptIndex(0);
    session.setState(SessionState.PROCESSING);
    session.setRawUrl("D:/data/raw/sess_001/IMG_001.jpg");
    session.setUpdatedAt(OffsetDateTime.now());

    V2TemplateRef ref = new V2TemplateRef(
        "tpl_001",
        "0.2.0",
        "http://localhost:9002/templates/tpl_001-0.2.0.zip",
        "abc123"
    );

    when(templateResolveService.resolveForV2("tpl_001")).thenReturn(ref);

    AiGatewayV2Client.Result clientResult = AiGatewayV2Client.Result.ok(
        "/files/job123/preview.jpg",
        "/files/job123/final.jpg",
        Map.of("totalMs", 1234)
    );
    when(aiGatewayV2Client.process(
        eq("tpl_001"),
        eq("0.2.0"),
        anyString(),
        anyString(),
        eq("D:/data/raw/sess_001/IMG_001.jpg")
    )).thenReturn(clientResult);

    when(boothProps.getGatewayBaseUrl()).thenReturn("http://127.0.0.1:9001");

    // Act
    service.process(session);

    // Assert
    assertEquals("http://127.0.0.1:9001/files/job123/preview.jpg", session.getPreviewUrl());
    assertEquals("http://127.0.0.1:9001/files/job123/final.jpg", session.getFinalUrl());
    assertNotNull(session.getProgress());
    assertEquals(95, session.getProgress().getPercent());
    assertNotNull(session.getUpdatedAt());
    assertNull(session.getError());
  }
}

