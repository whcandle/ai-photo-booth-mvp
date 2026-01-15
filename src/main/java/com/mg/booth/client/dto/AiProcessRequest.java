package com.mg.booth.client.dto;

import jdk.jfr.DataAmount;
import lombok.Data;

import java.util.Map;
@Data
public class AiProcessRequest {
  private String sessionId;
  private int attemptIndex;
  private String templateId;
  private String rawPath;

//  private Map<String, Object> template;
  private Map<String, Object> options;  // e.g., {"style":"cartoon","resolution":"4k"}
  private Map<String, Object> output;  // e.g., {"format":"png","quality":"high"}

}
