package com.mg.booth.camera;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 相机配置数据模型（对应 camera.json）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CameraConfig {
  
  private String cameraModel = "Canon EOS R6";
  private String selectedCameraId = "auto";
  private String activePresetId;
  private String basePresetId;  // 保存原始预设ID（当 activePresetId = "preset_custom" 时使用）
  
  private CameraParams params = new CameraParams();
  private List<CameraPreset> presets = new ArrayList<>();
  private CameraUiConfig ui = new CameraUiConfig();

  public CameraConfig() {
    // 初始化默认 presets
    initDefaultPresets();
  }

  private void initDefaultPresets() {
    // 白天·室外
    presets.add(new CameraPreset(
        "preset_day_outdoor",
        "白天·室外",
        List.of("day", "outdoor"),
        new CameraParams(100, "DAYLIGHT", 0.0, "STANDARD", "F4.0", "1/250", "EVALUATIVE")
    ));
    
    // 夜晚·室内
    presets.add(new CameraPreset(
        "preset_night_indoor",
        "夜晚·室内",
        List.of("night", "indoor"),
        new CameraParams(1600, "TUNGSTEN", 0.3, "PORTRAIT", "F2.8", "1/60", "CENTER_WEIGHTED")
    ));
    
    // 白天·室内
    presets.add(new CameraPreset(
        "preset_day_indoor",
        "白天·室内",
        List.of("day", "indoor"),
        new CameraParams(400, "FLUORESCENT", 0.0, "STANDARD", "F4.0", "1/125", "EVALUATIVE")
    ));
    
    // 夜晚·室外
    presets.add(new CameraPreset(
        "preset_night_outdoor",
        "夜晚·室外",
        List.of("night", "outdoor"),
        new CameraParams(800, "DAYLIGHT", 0.0, "STANDARD", "F2.8", "1/125", "EVALUATIVE")
    ));
    
    // 设置环境预设的 category 和 displayName
    for (CameraPreset preset : presets) {
      if (preset.getCategory() == null) {
        preset.setCategory("ENV");
        preset.setDisplayName(preset.getName());
      }
    }
    
    // 添加 4 个业务场景预设（映射到旧的 CameraProfileService）
    // 1. 医疗/养老标准记录
    presets.add(new CameraPreset(
        "preset_business_medical",
        "医疗/养老标准记录",
        "医疗/养老标准记录",
        "BUSINESS",
        "medical_standard",  // 对应 CameraProfileService 的 profile ID
        List.of("business", "medical", "elderly"),
        new CameraParams(0, "AUTO", 0.0, "STANDARD", null, null, null)  // 占位参数，实际由 legacyProfileId 控制
    ));
    
    // 2. 证件照/工牌
    presets.add(new CameraPreset(
        "preset_business_idphoto",
        "证件照/工牌",
        "证件照/工牌",
        "BUSINESS",
        "id_photo",  // 对应 CameraProfileService 的 profile ID
        List.of("business", "id", "photo"),
        new CameraParams(0, "DAYLIGHT", 0.0, "STANDARD", null, null, null)  // 占位参数
    ));
    
    // 3. 展会/活动讨喜
    presets.add(new CameraPreset(
        "preset_business_expo_pretty",
        "展会/活动讨喜",
        "展会/活动讨喜",
        "BUSINESS",
        "event_marketing",  // 对应 CameraProfileService 的 profile ID
        List.of("business", "event", "marketing"),
        new CameraParams(0, "AUTO", 0.0, "PORTRAIT", null, null, null)  // 占位参数
    ));
    
    // 4. 养老记录/家属留存
    presets.add(new CameraPreset(
        "preset_business_family_archive",
        "养老记录/家属留存",
        "养老记录/家属留存",
        "BUSINESS",
        "elder_care",  // 对应 CameraProfileService 的 profile ID
        List.of("business", "elder", "family"),
        new CameraParams(0, "CLOUDY", 0.0, "PORTRAIT", null, null, null)  // 占位参数
    ));
    
    // 默认使用第一个 preset
    if (!presets.isEmpty()) {
      activePresetId = presets.get(0).getId();
      params = new CameraParams(presets.get(0).getParams());
    }
  }

  // Getters and Setters
  public String getCameraModel() {
    return cameraModel;
  }

  public void setCameraModel(String cameraModel) {
    this.cameraModel = cameraModel;
  }

  public String getSelectedCameraId() {
    return selectedCameraId;
  }

  public void setSelectedCameraId(String selectedCameraId) {
    this.selectedCameraId = selectedCameraId;
  }

  public String getActivePresetId() {
    return activePresetId;
  }

  public void setActivePresetId(String activePresetId) {
    this.activePresetId = activePresetId;
  }

  public String getBasePresetId() {
    return basePresetId;
  }

  public void setBasePresetId(String basePresetId) {
    this.basePresetId = basePresetId;
  }

  public CameraParams getParams() {
    return params;
  }

  public void setParams(CameraParams params) {
    this.params = params;
  }

  public List<CameraPreset> getPresets() {
    return presets;
  }

  public void setPresets(List<CameraPreset> presets) {
    this.presets = presets;
  }

  public CameraUiConfig getUi() {
    return ui;
  }

  public void setUi(CameraUiConfig ui) {
    this.ui = ui;
  }

  /**
   * 相机参数（7个核心参数）
   */
  public static class CameraParams {
    private Integer iso;
    private String whiteBalance;
    private Double exposureCompensationEv;
    private String pictureStyle;
    private String aperture;
    private String shutterSpeed;
    private String meteringMode;

    public CameraParams() {
    }

    public CameraParams(Integer iso, String whiteBalance, Double exposureCompensationEv,
                       String pictureStyle, String aperture, String shutterSpeed, String meteringMode) {
      this.iso = iso;
      this.whiteBalance = whiteBalance;
      this.exposureCompensationEv = exposureCompensationEv;
      this.pictureStyle = pictureStyle;
      this.aperture = aperture;
      this.shutterSpeed = shutterSpeed;
      this.meteringMode = meteringMode;
    }

    public CameraParams(CameraParams other) {
      this.iso = other.iso;
      this.whiteBalance = other.whiteBalance;
      this.exposureCompensationEv = other.exposureCompensationEv;
      this.pictureStyle = other.pictureStyle;
      this.aperture = other.aperture;
      this.shutterSpeed = other.shutterSpeed;
      this.meteringMode = other.meteringMode;
    }

    // Getters and Setters
    public Integer getIso() {
      return iso;
    }

    public void setIso(Integer iso) {
      this.iso = iso;
    }

    public String getWhiteBalance() {
      return whiteBalance;
    }

    public void setWhiteBalance(String whiteBalance) {
      this.whiteBalance = whiteBalance;
    }

    public Double getExposureCompensationEv() {
      return exposureCompensationEv;
    }

    public void setExposureCompensationEv(Double exposureCompensationEv) {
      this.exposureCompensationEv = exposureCompensationEv;
    }

    public String getPictureStyle() {
      return pictureStyle;
    }

    public void setPictureStyle(String pictureStyle) {
      this.pictureStyle = pictureStyle;
    }

    public String getAperture() {
      return aperture;
    }

    public void setAperture(String aperture) {
      this.aperture = aperture;
    }

    public String getShutterSpeed() {
      return shutterSpeed;
    }

    public void setShutterSpeed(String shutterSpeed) {
      this.shutterSpeed = shutterSpeed;
    }

    public String getMeteringMode() {
      return meteringMode;
    }

    public void setMeteringMode(String meteringMode) {
      this.meteringMode = meteringMode;
    }
  }

  /**
   * 套餐预设
   */
  public static class CameraPreset {
    private String id;
    private String name;
    private List<String> tags;
    private CameraParams params;
    
    // 新增字段：用于业务场景预设
    private String legacyProfileId;  // 映射到旧 CameraProfileService 的 profile ID
    private String displayName;       // 中文显示名称
    private String category;          // 分类：BUSINESS（业务场景）或 ENV（环境预设）

    public CameraPreset() {
    }

    public CameraPreset(String id, String name, List<String> tags, CameraParams params) {
      this.id = id;
      this.name = name;
      this.tags = tags;
      this.params = params;
    }
    
    // 用于创建业务场景预设的构造函数
    public CameraPreset(String id, String name, String displayName, String category, 
                       String legacyProfileId, List<String> tags, CameraParams params) {
      this.id = id;
      this.name = name;
      this.displayName = displayName;
      this.category = category;
      this.legacyProfileId = legacyProfileId;
      this.tags = tags;
      this.params = params;
    }

    // Getters and Setters
    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getTags() {
      return tags;
    }

    public void setTags(List<String> tags) {
      this.tags = tags;
    }

    public CameraParams getParams() {
      return params;
    }

    public void setParams(CameraParams params) {
      this.params = params;
    }

    public String getLegacyProfileId() {
      return legacyProfileId;
    }

    public void setLegacyProfileId(String legacyProfileId) {
      this.legacyProfileId = legacyProfileId;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }
  }

  /**
   * UI 配置
   */
  public static class CameraUiConfig {
    private Boolean lockOnCountdown = true;
    private Boolean autoRestoreAfterSession = false;

    public Boolean getLockOnCountdown() {
      return lockOnCountdown;
    }

    public void setLockOnCountdown(Boolean lockOnCountdown) {
      this.lockOnCountdown = lockOnCountdown;
    }

    public Boolean getAutoRestoreAfterSession() {
      return autoRestoreAfterSession;
    }

    public void setAutoRestoreAfterSession(Boolean autoRestoreAfterSession) {
      this.autoRestoreAfterSession = autoRestoreAfterSession;
    }
  }
}
