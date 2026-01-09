package com.mg.booth.api;

import com.mg.booth.service.DeliveryService;
import com.mg.booth.service.SessionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
public class DeliveryController {

  private final DeliveryService deliveryService;
  private final SessionService sessionService;

  public DeliveryController(DeliveryService deliveryService, SessionService sessionService) {
    this.deliveryService = deliveryService;
    this.sessionService = sessionService;
  }

  @GetMapping(value = "/d/{token}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<byte[]> deliveryPage(@PathVariable String token) {
    var rec = deliveryService.getValid(token);
    if (rec == null) {
      String html = "<html><body><h3>Link expired or invalid</h3></body></html>";
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.TEXT_HTML)
        .body(html.getBytes(StandardCharsets.UTF_8));
    }

    var s = sessionService.get(rec.getSessionId());
    String finalUrl = (s.getFinalUrl() == null) ? "" : s.getFinalUrl();
    String html = """
      <html>
      <head><meta charset="utf-8"><title>AI Photo Booth</title></head>
      <body style="font-family: Arial; padding: 24px;">
        <h2>AI Photo Booth</h2>
        <p>点击下方按钮下载照片：</p>
        <p><a href="/d/%s/download">Download</a></p>
        <hr/>
        <p>预览（可选）：</p>
        <img src="%s" style="max-width: 100%%; border: 1px solid #ddd;" />
      </body>
      </html>
      """.formatted(token, finalUrl);

    return ResponseEntity.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(html.getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/d/{token}/download")
  public ResponseEntity<Resource> download(@PathVariable String token) {
    var rec = deliveryService.getValid(token);
    if (rec == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    var s = sessionService.get(rec.getSessionId());
    if (s.getFinalUrl() == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // finalUrl 形如 /files/final/{sessionId}/{attempt}.jpg
    // 直接映射到 storage/final/{sessionId}/{attempt}.jpg
    String fileName = s.getAttemptIndex() + ".jpg";
    Path p = Path.of("storage", "final", s.getSessionId(), fileName);

    FileSystemResource res = new FileSystemResource(p.toFile());
    if (!res.exists()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename("photo.jpg").build());
    headers.setCacheControl(CacheControl.noCache());
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

    return ResponseEntity.ok().headers(headers).body(res);
  }
}
