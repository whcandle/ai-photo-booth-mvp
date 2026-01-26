package com.mg.booth.domain;

import java.util.Map;

/**
 * 相机策略配置（场景参数组合）
 * 用于将业务场景映射到一组相机属性值
 */
public class CameraProfile {
    /**
     * 策略ID（内部标识）
     */
    private String id;

    /**
     * 策略名称（内部标识，英文）
     */
    private String name;

    /**
     * 显示名称（用户可见）
     */
    private String displayName;

    /**
     * 属性键值对
     * Key: 属性键（ISO, WB, ExposureComp, PictureStyle）
     * Value: EDSDK 编码值（不是直接数值）
     */
    private Map<String, Integer> props;

    /**
     * 策略描述（可选）
     */
    private String description;

    public CameraProfile() {
    }

    public CameraProfile(String id, String name, String displayName, Map<String, Integer> props) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.props = props;
    }

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<String, Integer> getProps() {
        return props;
    }

    public void setProps(Map<String, Integer> props) {
        this.props = props;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
