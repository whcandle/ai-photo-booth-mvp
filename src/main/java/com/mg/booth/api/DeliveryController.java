package com.mg.booth.api;

import com.mg.booth.service.DeliveryService;
import com.mg.booth.service.SessionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.web.client.RestClient;


@RestController
public class DeliveryController {

  private final DeliveryService deliveryService;
  private final SessionService sessionService;

  private final RestClient http = RestClient.builder().build();


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
    // 预览图片和下载按钮都指向本服务的 /d/{token}/download endpoint
    // 使用 ?preview=true 参数让图片可以内联显示，下载按钮不带参数则触发下载
    String previewUrl = "/d/" + token + "/download?preview=true";
    String downloadUrl = "/d/" + token + "/download";
    
    String html = """
      <html>
      <head><meta charset="utf-8"><title>AI Photo Booth</title></head>
      <body style="font-family: Arial; padding: 24px;">
        <h2>AI Photo Booth</h2>
        <p>点击下方按钮下载照片：</p>
        <p><a href="%s" style="display: inline-block; padding: 12px 24px; background: #2d6cff; color: white; text-decoration: none; border-radius: 8px;">下载照片</a></p>
        <hr/>
        <p>预览：</p>
        <img src="%s" style="max-width: 100%%; border: 1px solid #ddd;" alt="预览图" />
      </body>
      </html>
      """.formatted(downloadUrl, previewUrl);

    return ResponseEntity.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(html.getBytes(StandardCharsets.UTF_8));
  }

//  @GetMapping("/d/{token}/download")
//  public ResponseEntity<Resource> download(@PathVariable String token) {
//    var rec = deliveryService.getValid(token);
//    if (rec == null) {
//      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//    }
//
//    var s = sessionService.get(rec.getSessionId());
//    if (s.getFinalUrl() == null) {
//      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//    }
//
//    // finalUrl 形如 /files/final/{sessionId}/{attempt}.jpg
//    // 直接映射到 storage/final/{sessionId}/{attempt}.jpg
//    String fileName = s.getAttemptIndex() + ".jpg";
//    Path p = Path.of("storage", "final", s.getSessionId(), fileName);
//
//    FileSystemResource res = new FileSystemResource(p.toFile());
//    if (!res.exists()) {
//      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//    }
//
//    HttpHeaders headers = new HttpHeaders();
//    headers.setContentDisposition(ContentDisposition.attachment().filename("photo.jpg").build());
//    headers.setCacheControl(CacheControl.noCache());
//    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//
//    return ResponseEntity.ok().headers(headers).body(res);
//  }

  @GetMapping("/d/{token}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable String token,
      @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview) {
    var rec = deliveryService.getValid(token);
    if (rec == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    var s = sessionService.get(rec.getSessionId());
    String finalUrl = s.getFinalUrl();
    if (finalUrl == null || finalUrl.isBlank()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    try {
      // ✅ 代理拉取图片内容（不暴露 9002 给浏览器）
      var upstream = http.get()
              .uri(finalUrl)
              .retrieve()
              .toEntity(byte[].class);

      byte[] body = upstream.getBody();
      if (body == null || body.length == 0) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
      }

      MediaType ct = upstream.getHeaders().getContentType();
      if (ct == null) ct = MediaType.IMAGE_JPEG; // 默认 jpg

      HttpHeaders headers = new HttpHeaders();
      
      // 如果是预览模式（preview=true），使用 inline 让图片可以在浏览器中显示
      // 如果是下载模式（默认），使用 attachment 触发下载
      if (preview) {
        headers.setContentDisposition(
            ContentDisposition.inline().filename("photo.jpg").build()
        );
      } else {
        headers.setContentDisposition(
            ContentDisposition.attachment().filename("photo.jpg").build()
        );
      }
      
      headers.setCacheControl(CacheControl.noCache());
      headers.setContentType(ct);

      return ResponseEntity.ok().headers(headers).body(body);

    } catch (Exception e) {
      // 上游 9002 不通/404/读失败
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
  }



}
