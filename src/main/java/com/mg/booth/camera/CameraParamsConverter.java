package com.mg.booth.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 相机参数转换工具
 * 将可读的字符串参数值转换为 EDSDK 编码值（Integer）
 * 
 * 支持 7 个参数：ISO、WB、ExposureComp、PictureStyle、Aperture、ShutterSpeed、MeteringMode
 */
@Component
public class CameraParamsConverter {
  
  private static final Logger log = LoggerFactory.getLogger(CameraParamsConverter.class);

  /**
   * ISO 值映射（字符串 -> EDSDK 编码值）
   * 注意：这些值需要根据实际相机型号调整
   */
  private static final Map<String, Integer> ISO_MAP = new HashMap<>();
  static {
    ISO_MAP.put("AUTO", 0);
    ISO_MAP.put("100", 0x00000048);  // ISO 100
    ISO_MAP.put("200", 0x00000050);  // ISO 200
    ISO_MAP.put("400", 0x00000058);  // ISO 400
    ISO_MAP.put("800", 0x00000060);  // ISO 800
    ISO_MAP.put("1600", 0x00000068); // ISO 1600
    ISO_MAP.put("3200", 0x00000070); // ISO 3200
    ISO_MAP.put("6400", 0x00000078); // ISO 6400
  }

  /**
   * 白平衡值映射（字符串 -> EDSDK 编码值）
   */
  private static final Map<String, Integer> WB_MAP = new HashMap<>();
  static {
    WB_MAP.put("AUTO", 0);
    WB_MAP.put("DAYLIGHT", 1);
    WB_MAP.put("SHADE", 2);
    WB_MAP.put("CLOUDY", 3);
    WB_MAP.put("TUNGSTEN", 4);
    WB_MAP.put("FLUORESCENT", 5);
    WB_MAP.put("FLASH", 6);
    WB_MAP.put("KELVIN", 7);
  }

  /**
   * 曝光补偿值映射（EV 值 -> EDSDK 编码值）
   * 注意：EDSDK 使用 1/3 EV 步进
   * 负数编码：232-253 (对应 -3.0 到 -0.3 EV)
   * 正数编码：0, 3, 5, 8, 11, 13, 16, 19, 21, 24 (对应 0.0 到 +3.0 EV)
   * 根据实际候选值：[232,235,237,240,243,245,248,251,253,0,3,5,8,11,13,16,19,21,24]
   */
  private static final Map<Double, Integer> EV_MAP = new HashMap<>();
  static {
    // 负数：-3.0 到 -0.3 EV
    EV_MAP.put(-3.0, 232);
    EV_MAP.put(-2.7, 235);
    EV_MAP.put(-2.3, 237);
    EV_MAP.put(-2.0, 240);
    EV_MAP.put(-1.7, 243);
    EV_MAP.put(-1.3, 245);
    EV_MAP.put(-1.0, 248);
    EV_MAP.put(-0.7, 251);
    EV_MAP.put(-0.3, 253);
    
    // 正数：0.0 到 +3.0 EV
    EV_MAP.put(0.0, 0);
    EV_MAP.put(0.3, 3);
    EV_MAP.put(0.7, 5);
    EV_MAP.put(1.0, 8);
    EV_MAP.put(1.3, 11);
    EV_MAP.put(1.7, 13);
    EV_MAP.put(2.0, 16);
    EV_MAP.put(2.3, 19);
    EV_MAP.put(2.7, 21);
    EV_MAP.put(3.0, 24);
  }

  /**
   * 光圈值映射（F值字符串 -> EDSDK 编码值）
   * 根据实际候选值：[21,24,27,29,32,35,37,40,43,45,48,51,53,56,59,61,64,67,69,72,75,77,80]
   * 常见值：24=F2.8, 32=F4.0, 40=F5.6, 72=F8.0
   */
  private static final Map<String, Integer> APERTURE_MAP = new HashMap<>();
  static {
    APERTURE_MAP.put("F1.4", 16);
    APERTURE_MAP.put("F1.8", 18);
    APERTURE_MAP.put("F2.0", 20);
    APERTURE_MAP.put("F2.2", 21);
    APERTURE_MAP.put("F2.5", 23);
    APERTURE_MAP.put("F2.8", 24);
    APERTURE_MAP.put("F3.2", 27);
    APERTURE_MAP.put("F3.5", 29);
    APERTURE_MAP.put("F4.0", 32);
    APERTURE_MAP.put("F4.5", 35);
    APERTURE_MAP.put("F5.0", 37);
    APERTURE_MAP.put("F5.6", 40);
    APERTURE_MAP.put("F6.3", 43);
    APERTURE_MAP.put("F7.1", 45);
    APERTURE_MAP.put("F8.0", 48);
    APERTURE_MAP.put("F9.0", 51);
    APERTURE_MAP.put("F10", 53);
    APERTURE_MAP.put("F11", 56);
    APERTURE_MAP.put("F13", 59);
    APERTURE_MAP.put("F14", 61);
    APERTURE_MAP.put("F16", 64);
    APERTURE_MAP.put("F18", 67);
    APERTURE_MAP.put("F20", 69);
    APERTURE_MAP.put("F22", 72);
    APERTURE_MAP.put("F25", 75);
    APERTURE_MAP.put("F29", 77);
    APERTURE_MAP.put("F32", 80);
  }

  /**
   * 快门速度值映射（字符串 -> EDSDK 编码值）
   * 注意：编码值越大，快门越快
   * 常见值：48=1/60s, 64=1/125s, 72=1/250s, 80=1/500s
   * 根据实际候选值范围，需要根据相机型号调整
   */
  private static final Map<String, Integer> SHUTTER_SPEED_MAP = new HashMap<>();
  static {
    // 慢速快门
    SHUTTER_SPEED_MAP.put("30", 16);      // 30s
    SHUTTER_SPEED_MAP.put("25", 19);      // 25s
    SHUTTER_SPEED_MAP.put("20", 21);      // 20s
    SHUTTER_SPEED_MAP.put("15", 24);      // 15s
    SHUTTER_SPEED_MAP.put("13", 27);      // 13s
    SHUTTER_SPEED_MAP.put("10", 29);      // 10s
    SHUTTER_SPEED_MAP.put("8", 32);       // 8s
    SHUTTER_SPEED_MAP.put("6", 35);       // 6s
    SHUTTER_SPEED_MAP.put("5", 37);       // 5s
    SHUTTER_SPEED_MAP.put("4", 40);       // 4s
    SHUTTER_SPEED_MAP.put("3", 43);       // 3s
    SHUTTER_SPEED_MAP.put("2.5", 45);     // 2.5s
    SHUTTER_SPEED_MAP.put("2", 48);       // 2s
    SHUTTER_SPEED_MAP.put("1.6", 51);     // 1.6s
    SHUTTER_SPEED_MAP.put("1.3", 53);     // 1.3s
    SHUTTER_SPEED_MAP.put("1", 56);       // 1s
    SHUTTER_SPEED_MAP.put("0.8", 59);     // 0.8s
    SHUTTER_SPEED_MAP.put("0.6", 61);     // 0.6s
    SHUTTER_SPEED_MAP.put("0.5", 64);     // 0.5s (1/2s)
    
    // 常用快门速度（分数形式）
    SHUTTER_SPEED_MAP.put("1/4", 72);     // 1/4s
    SHUTTER_SPEED_MAP.put("1/5", 75);     // 1/5s
    SHUTTER_SPEED_MAP.put("1/6", 77);     // 1/6s
    SHUTTER_SPEED_MAP.put("1/8", 80);     // 1/8s
    SHUTTER_SPEED_MAP.put("1/10", 83);    // 1/10s
    SHUTTER_SPEED_MAP.put("1/13", 85);    // 1/13s
    SHUTTER_SPEED_MAP.put("1/15", 88);    // 1/15s
    SHUTTER_SPEED_MAP.put("1/20", 91);    // 1/20s
    SHUTTER_SPEED_MAP.put("1/25", 93);    // 1/25s
    SHUTTER_SPEED_MAP.put("1/30", 96);    // 1/30s
    SHUTTER_SPEED_MAP.put("1/40", 99);    // 1/40s
    SHUTTER_SPEED_MAP.put("1/50", 101);   // 1/50s
    // 根据实际相机值调整（用户提供的实际值）
    SHUTTER_SPEED_MAP.put("1/60", 48);    // 根据实际值：48 = 1/60s
    SHUTTER_SPEED_MAP.put("1/80", 51);    // 估算
    SHUTTER_SPEED_MAP.put("1/100", 56);  // 估算
    SHUTTER_SPEED_MAP.put("1/125", 64);   // 根据实际值：64 = 1/125s
    SHUTTER_SPEED_MAP.put("1/160", 67);   // 估算
    SHUTTER_SPEED_MAP.put("1/200", 69);   // 估算
    SHUTTER_SPEED_MAP.put("1/250", 72);   // 根据实际值：72 = 1/250s
    SHUTTER_SPEED_MAP.put("1/320", 75);   // 估算
    SHUTTER_SPEED_MAP.put("1/400", 77);   // 估算
    SHUTTER_SPEED_MAP.put("1/500", 80);   // 根据实际值：80 = 1/500s
    SHUTTER_SPEED_MAP.put("1/640", 131);  // 1/640s（估算，需要根据实际相机调整）
    SHUTTER_SPEED_MAP.put("1/800", 133);  // 1/800s（估算）
    SHUTTER_SPEED_MAP.put("1/1000", 136); // 1/1000s（估算）
    SHUTTER_SPEED_MAP.put("1/1250", 139); // 1/1250s（估算）
    SHUTTER_SPEED_MAP.put("1/1600", 141); // 1/1600s（估算）
    SHUTTER_SPEED_MAP.put("1/2000", 144); // 1/2000s（估算）
    SHUTTER_SPEED_MAP.put("1/2500", 147); // 1/2500s（估算）
    SHUTTER_SPEED_MAP.put("1/3200", 149); // 1/3200s（估算）
    SHUTTER_SPEED_MAP.put("1/4000", 152); // 1/4000s（估算）
  }

  /**
   * 测光模式值映射（字符串 -> EDSDK 编码值）
   * 根据实际候选值：[3,4,1,5]
   * 1 = EVALUATIVE（评价测光）
   * 3 = PARTIAL（局部测光）
   * 4 = SPOT（点测光）
   * 5 = CENTER_WEIGHTED（中央重点测光）
   */
  private static final Map<String, Integer> METERING_MODE_MAP = new HashMap<>();
  static {
    METERING_MODE_MAP.put("EVALUATIVE", 1);
    METERING_MODE_MAP.put("PARTIAL", 3);
    METERING_MODE_MAP.put("SPOT", 4);
    METERING_MODE_MAP.put("CENTER_WEIGHTED", 5);
    METERING_MODE_MAP.put("CENTER_WEIGHTED_AVERAGE", 5); // 别名
  }

  /**
   * 画面风格值映射（字符串 -> EDSDK 编码值）
   * 注意：这些值需要根据实际相机型号调整
   */
  private static final Map<String, Integer> PICTURE_STYLE_MAP = new HashMap<>();
  static {
    PICTURE_STYLE_MAP.put("STANDARD", 129);  // 标准
    PICTURE_STYLE_MAP.put("PORTRAIT", 130);  // 人像
    PICTURE_STYLE_MAP.put("LANDSCAPE", 131); // 风光
    PICTURE_STYLE_MAP.put("NEUTRAL", 132);   // 中性
    PICTURE_STYLE_MAP.put("FAITHFUL", 133);  // 可靠设置
    PICTURE_STYLE_MAP.put("MONOCHROME", 134); // 单色
  }

  /**
   * 将 CameraParams 转换为 EDSDK 属性映射
   * 只转换支持的参数，不支持的参数会记录警告日志
   * 
   * @param params 相机参数
   * @return EDSDK 属性映射（key -> EDSDK 编码值）
   */
  public Map<String, Integer> convertToEdsdkProps(CameraConfig.CameraParams params) {
    Map<String, Integer> props = new HashMap<>();
    
    // ISO
    if (params.getIso() != null) {
      Integer isoCode = convertIso(params.getIso());
      if (isoCode != null) {
        props.put("ISO", isoCode);
      } else {
        log.warn("[params-converter] Unsupported ISO value: {}", params.getIso());
      }
    }
    
    // White Balance
    if (params.getWhiteBalance() != null) {
      Integer wbCode = convertWhiteBalance(params.getWhiteBalance());
      if (wbCode != null) {
        props.put("WB", wbCode);
      } else {
        log.warn("[params-converter] Unsupported WhiteBalance value: {}", params.getWhiteBalance());
      }
    }
    
    // Exposure Compensation
    if (params.getExposureCompensationEv() != null) {
      Integer evCode = convertExposureCompensation(params.getExposureCompensationEv());
      if (evCode != null) {
        props.put("ExposureComp", evCode);
      } else {
        log.warn("[params-converter] Unsupported ExposureCompensationEv value: {}", 
            params.getExposureCompensationEv());
      }
    }
    
    // Picture Style
    if (params.getPictureStyle() != null) {
      Integer styleCode = convertPictureStyle(params.getPictureStyle());
      if (styleCode != null) {
        props.put("PictureStyle", styleCode);
      } else {
        log.warn("[params-converter] Unsupported PictureStyle value: {}", params.getPictureStyle());
      }
    }
    
    // Aperture
    if (params.getAperture() != null) {
      Integer apertureCode = convertAperture(params.getAperture());
      if (apertureCode != null) {
        props.put("APERTURE", apertureCode);
      } else {
        log.warn("[params-converter] Unsupported Aperture value: {}", params.getAperture());
      }
    }
    
    // Shutter Speed
    if (params.getShutterSpeed() != null) {
      Integer shutterCode = convertShutterSpeed(params.getShutterSpeed());
      if (shutterCode != null) {
        props.put("SHUTTER_SPEED", shutterCode);
      } else {
        log.warn("[params-converter] Unsupported ShutterSpeed value: {}", params.getShutterSpeed());
      }
    }
    
    // Metering Mode
    if (params.getMeteringMode() != null) {
      Integer meteringCode = convertMeteringMode(params.getMeteringMode());
      if (meteringCode != null) {
        props.put("METERING_MODE", meteringCode);
      } else {
        log.warn("[params-converter] Unsupported MeteringMode value: {}", params.getMeteringMode());
      }
    }
    
    return props;
  }

  /**
   * 转换 ISO 值
   */
  private Integer convertIso(Integer iso) {
    if (iso == null) {
      return null;
    }
    String key = String.valueOf(iso);
    Integer code = ISO_MAP.get(key);
    if (code == null && iso == 0) {
      return ISO_MAP.get("AUTO");
    }
    return code;
  }

  /**
   * 转换白平衡值
   */
  private Integer convertWhiteBalance(String wb) {
    if (wb == null || wb.isBlank()) {
      return null;
    }
    return WB_MAP.get(wb.toUpperCase());
  }

  /**
   * 转换曝光补偿值
   */
  private Integer convertExposureCompensation(Double ev) {
    if (ev == null) {
      return null;
    }
    // 四舍五入到最近的 0.3 步进（精确匹配）
    // 例如：0.3 -> 0.3, 0.4 -> 0.3, 0.5 -> 0.7
    double rounded = Math.round(ev * 10.0 / 3.0) * 3.0 / 10.0;
    // 处理浮点数精度问题
    rounded = Math.round(rounded * 10.0) / 10.0;
    Integer code = EV_MAP.get(rounded);
    if (code == null) {
      // 如果精确匹配失败，尝试最接近的值
      double minDiff = Double.MAX_VALUE;
      Double closestKey = null;
      for (Double key : EV_MAP.keySet()) {
        double diff = Math.abs(key - rounded);
        if (diff < minDiff) {
          minDiff = diff;
          closestKey = key;
        }
      }
      if (closestKey != null && minDiff < 0.5) {
        code = EV_MAP.get(closestKey);
        log.debug("[params-converter] ExposureComp {} rounded to {} (code={})", ev, closestKey, code);
      }
    }
    return code;
  }

  /**
   * 转换画面风格值
   */
  private Integer convertPictureStyle(String style) {
    if (style == null || style.isBlank()) {
      return null;
    }
    return PICTURE_STYLE_MAP.get(style.toUpperCase());
  }

  /**
   * 转换光圈值
   */
  private Integer convertAperture(String aperture) {
    if (aperture == null || aperture.isBlank()) {
      return null;
    }
    // 标准化格式：确保是 "F" + 数字格式
    String normalized = aperture.toUpperCase().trim();
    if (!normalized.startsWith("F")) {
      normalized = "F" + normalized;
    }
    return APERTURE_MAP.get(normalized);
  }

  /**
   * 转换快门速度值
   */
  private Integer convertShutterSpeed(String shutterSpeed) {
    if (shutterSpeed == null || shutterSpeed.isBlank()) {
      return null;
    }
    // 标准化格式：处理 "1/60", "1/125" 等格式
    String normalized = shutterSpeed.trim();
    return SHUTTER_SPEED_MAP.get(normalized);
  }

  /**
   * 转换测光模式值
   */
  private Integer convertMeteringMode(String meteringMode) {
    if (meteringMode == null || meteringMode.isBlank()) {
      return null;
    }
    return METERING_MODE_MAP.get(meteringMode.toUpperCase());
  }
}
