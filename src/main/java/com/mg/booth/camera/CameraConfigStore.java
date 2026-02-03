package com.mg.booth.camera;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * camera.json 读写工具
 * 如果文件不存在，自动生成默认配置
 */
@Component
public class CameraConfigStore {
  private static final Logger log = LoggerFactory.getLogger(CameraConfigStore.class);

  private final ObjectMapper om;

  public CameraConfigStore() {
    this.om = new ObjectMapper();
    this.om.registerModule(new JavaTimeModule());
    this.om.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * 加载 camera.json，如果文件不存在则生成默认配置并保存
   * 
   * @param file 配置文件路径
   * @return CameraConfig，如果加载失败则返回默认配置
   */
  public CameraConfig load(Path file) {
    try {
      if (!Files.exists(file)) {
        log.info("[camera-config] camera.json not found at {}, creating default config", file.toAbsolutePath());
        CameraConfig defaultConfig = new CameraConfig();
        save(file, defaultConfig);
        return defaultConfig;
      }

      String json = Files.readString(file);
      CameraConfig config = om.readValue(json, CameraConfig.class);
      
      // 确保 presets 不为空（兼容旧版本）
      if (config.getPresets() == null || config.getPresets().isEmpty()) {
        log.warn("[camera-config] presets is empty, initializing defaults");
        CameraConfig defaultConfig = new CameraConfig();
        config.setPresets(defaultConfig.getPresets());
        if (config.getActivePresetId() == null && !config.getPresets().isEmpty()) {
          config.setActivePresetId(config.getPresets().get(0).getId());
        }
      } else {
        // 检查并合并新的 business presets（如果缺失）
        CameraConfig defaultConfig = new CameraConfig();
        boolean needsUpdate = false;
        
        // 检查是否缺少 business presets
        boolean hasBusinessPresets = config.getPresets().stream()
            .anyMatch(p -> p.getId() != null && p.getId().startsWith("preset_business_"));
        
        if (!hasBusinessPresets) {
          log.info("[camera-config] Business presets not found, merging from defaults");
          // 从默认配置中获取 business presets
          for (CameraConfig.CameraPreset defaultPreset : defaultConfig.getPresets()) {
            if (defaultPreset.getId() != null && defaultPreset.getId().startsWith("preset_business_")) {
              // 检查是否已存在（避免重复）
              boolean exists = config.getPresets().stream()
                  .anyMatch(p -> p.getId() != null && p.getId().equals(defaultPreset.getId()));
              if (!exists) {
                config.getPresets().add(defaultPreset);
                needsUpdate = true;
                log.debug("[camera-config] Added business preset: {}", defaultPreset.getId());
              }
            }
          }
        }
        
        // 确保所有环境预设都有 category 和 displayName
        for (CameraConfig.CameraPreset preset : config.getPresets()) {
          if (preset.getCategory() == null) {
            preset.setCategory("ENV");
            needsUpdate = true;
          }
          if (preset.getDisplayName() == null) {
            preset.setDisplayName(preset.getName());
            needsUpdate = true;
          }
        }
        
        // 如果更新了配置，保存回文件
        if (needsUpdate) {
          log.info("[camera-config] Config updated with business presets, saving to file");
          save(file, config);
        }
      }
      
      // 确保 params 不为空
      if (config.getParams() == null) {
        log.warn("[camera-config] params is null, initializing defaults");
        config.setParams(new CameraConfig.CameraParams());
      }
      
      // 确保 ui 不为空
      if (config.getUi() == null) {
        log.warn("[camera-config] ui is null, initializing defaults");
        config.setUi(new CameraConfig.CameraUiConfig());
      }

      log.debug("[camera-config] Loaded config from {}", file.toAbsolutePath());
      return config;
    } catch (Exception e) {
      log.error("[camera-config] Failed to load camera config from {}: {}", 
          file.toAbsolutePath(), e.getMessage(), e);
      // 返回默认配置，不抛异常
      log.warn("[camera-config] Returning default config due to load error");
      return new CameraConfig();
    }
  }

  /**
   * 保存 camera.json
   * 
   * @param file 配置文件路径
   * @param config 配置对象
   */
  public void save(Path file, CameraConfig config) {
    try {
      String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(config);
      
      // 确保父目录存在
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      
      Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      log.info("[camera-config] camera.json saved to {}", file.toAbsolutePath());
    } catch (Exception e) {
      log.error("[camera-config] Failed to save camera config to {}: {}", 
          file.toAbsolutePath(), e.getMessage(), e);
      throw new RuntimeException("Failed to save camera config", e);
    }
  }

  /**
   * 获取配置文件路径（用于日志等）
   */
  public Path getConfigPath(String configFile) {
    return Path.of(configFile);
  }
}
