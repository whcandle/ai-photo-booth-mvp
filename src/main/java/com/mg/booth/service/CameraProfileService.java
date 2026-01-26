package com.mg.booth.service;

import com.mg.booth.camera.CameraService;
import com.mg.booth.domain.CameraProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 相机策略服务
 * 管理场景参数组合（策略）
 */
@Service
public class CameraProfileService {

    private static final Logger log = LoggerFactory.getLogger(CameraProfileService.class);

    @Autowired
    private CameraService cameraService;

    /**
     * 内置策略列表（写死在代码中，后续可迁移到配置文件）
     * 注意：属性值都是 EDSDK 编码值，需要根据实际相机型号调整
     */
    private static final List<CameraProfile> BUILTIN_PROFILES = new ArrayList<>();

    static {
        // 注意：PictureStyle 值需要根据实际相机型号调整
        // 从日志看，候选值是 [135, 129, 130, 131, 136, 132, 133, 134, 33, 34, 35]
        // 建议：先在相机上设置想要的风格，然后调用 GET /property/get?key=PictureStyle 获取编码值
        // 这里使用 129 作为默认值（通常是 Standard/标准），实际使用时需要根据相机调整
        
        // 1. 医疗/养老标准记录
        Map<String, Integer> medicalProps = new HashMap<>();
        medicalProps.put("WB", 0);  // Auto（根据实际灯光可调整为 Daylight/Fluorescent）
        medicalProps.put("ISO", 0);  // Auto
        medicalProps.put("ExposureComp", 0);  // 0 EV（或 -1/3 防过曝）
        medicalProps.put("PictureStyle", 129);  // Standard（标准，根据实际候选值调整，常见值：129=Standard, 130=Portrait, 131=Landscape）
        BUILTIN_PROFILES.add(new CameraProfile(
            "medical_standard",
            "MEDICAL_STANDARD",
            "医疗/养老标准记录",
            medicalProps
        ));

        // 2. 证件照/工牌
        Map<String, Integer> idPhotoProps = new HashMap<>();
        idPhotoProps.put("WB", 1);  // Daylight（固定，避免自动飘）
        idPhotoProps.put("ISO", 0);  // Auto 或较低固定值
        idPhotoProps.put("ExposureComp", 0);  // 0 EV 或 +1/3（防暗）
        idPhotoProps.put("PictureStyle", 129);  // Standard（标准，偏真实）
        BUILTIN_PROFILES.add(new CameraProfile(
            "id_photo",
            "ID_PHOTO",
            "证件照/工牌",
            idPhotoProps
        ));

        // 3. 展会/活动讨喜
        Map<String, Integer> eventProps = new HashMap<>();
        eventProps.put("WB", 0);  // Auto（灯光复杂时可 Auto）
        eventProps.put("ISO", 0);  // Auto
        eventProps.put("ExposureComp", 0);  // +1/3 ~ +2/3（更亮更讨喜，根据实际候选值调整）
        eventProps.put("PictureStyle", 130);  // Portrait（人像，根据实际候选值调整，常见值：130=Portrait）
        BUILTIN_PROFILES.add(new CameraProfile(
            "event_marketing",
            "EVENT_MARKETING",
            "展会/活动讨喜",
            eventProps
        ));

        // 4. 养老记录/家属留存
        Map<String, Integer> elderCareProps = new HashMap<>();
        elderCareProps.put("WB", 2);  // Cloudy（偏暖）
        elderCareProps.put("ISO", 0);  // Auto
        elderCareProps.put("ExposureComp", 0);  // +1/3（脸更亮）
        elderCareProps.put("PictureStyle", 130);  // Portrait（人像，避免过度锐化，常见值：130=Portrait）
        BUILTIN_PROFILES.add(new CameraProfile(
            "elder_care",
            "ELDER_CARE",
            "养老记录/家属留存",
            elderCareProps
        ));
    }

    /**
     * 获取所有可用策略列表
     */
    public List<CameraProfile> listProfiles() {
        return new ArrayList<>(BUILTIN_PROFILES);
    }

    /**
     * 根据ID获取策略
     */
    public CameraProfile getProfile(String id) {
        return BUILTIN_PROFILES.stream()
            .filter(p -> p.getId().equals(id) || p.getName().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * 应用策略到相机
     * @param profileId 策略ID
     * @param persist 是否持久化到配置文件
     * @return 应用结果（成功/失败、失败的属性键）
     */
    public ApplyProfileResult applyProfile(String profileId, boolean persist) {
        CameraProfile profile = getProfile(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }

        log.info("Applying camera profile: id={}, name={}, persist={}", 
            profile.getId(), profile.getName(), persist);

        ApplyProfileResult result = new ApplyProfileResult();
        result.setProfileId(profileId);
        result.setProfileName(profile.getName());
        result.setPersist(persist);

        Map<String, Integer> props = profile.getProps();
        List<String> failedKeys = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : props.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            try {
                cameraService.setProperty(key, value, persist);
                result.getAppliedProps().put(key, value);
                log.debug("Applied property: {} = {}", key, value);
            } catch (Exception ex) {
                log.warn("Failed to apply property {} = {}: {}", key, value, ex.getMessage());
                failedKeys.add(key);
                result.getFailedProps().put(key, ex.getMessage());
            }
        }

        result.setSuccess(failedKeys.isEmpty());
        if (!result.isSuccess()) {
            log.warn("Profile application partially failed: profileId={}, failedKeys={}", 
                profileId, failedKeys);
        } else {
            log.info("Profile applied successfully: profileId={}", profileId);
        }

        return result;
    }

    /**
     * 应用策略结果
     */
    public static class ApplyProfileResult {
        private String profileId;
        private String profileName;
        private boolean persist;
        private boolean success;
        private Map<String, Integer> appliedProps = new HashMap<>();
        private Map<String, String> failedProps = new HashMap<>();

        public String getProfileId() {
            return profileId;
        }

        public void setProfileId(String profileId) {
            this.profileId = profileId;
        }

        public String getProfileName() {
            return profileName;
        }

        public void setProfileName(String profileName) {
            this.profileName = profileName;
        }

        public boolean isPersist() {
            return persist;
        }

        public void setPersist(boolean persist) {
            this.persist = persist;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Map<String, Integer> getAppliedProps() {
            return appliedProps;
        }

        public void setAppliedProps(Map<String, Integer> appliedProps) {
            this.appliedProps = appliedProps;
        }

        public Map<String, String> getFailedProps() {
            return failedProps;
        }

        public void setFailedProps(Map<String, String> failedProps) {
            this.failedProps = failedProps;
        }
    }
}
