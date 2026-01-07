package com.mg.booth.api;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
public class FilesController {

  @GetMapping("/files/{type}/{sessionId}/{fileName}")
  public ResponseEntity<Resource> getFile(
    @PathVariable String type,
    @PathVariable String sessionId,
    @PathVariable String fileName
  ) {
    // Day3: only raw is guaranteed
    if (!type.equals("raw") && !type.equals("preview") && !type.equals("final")) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    Path p = Path.of("storage", type, sessionId, fileName);
    FileSystemResource res = new FileSystemResource(p.toFile());

    if (!res.exists()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setCacheControl(CacheControl.noCache());
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

    return ResponseEntity.ok().headers(headers).body(res);
  }
}

