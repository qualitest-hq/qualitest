package com.qualitest.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * IDEA 插件（及其他上传端）的接口导入上传类型。
 * <p>
 * 三种入口共用 {@code POST /api/project/importApis}，用本字段区分来源，便于后续按类型扩展策略
 * （例如仅项目级全量上传时种子项目鉴权配置）。
 */
public enum ApiImportUploadType {

    /** Tools → 项目级上传（整工程扫描） */
    PROJECT("project"),

    /** 编辑器右键：当前 Controller 上传全部 */
    CONTROLLER_ALL("controllerAll"),

    /** 编辑器右键：当前 Controller 选择上传 */
    CONTROLLER_SELECT("controllerSelect");

    private final String code;

    ApiImportUploadType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 是否在项目鉴权配置为空时写入双端 Bearer 默认模板。
     * 仅项目级全量上传开启，避免单 Controller 上传误改项目配置。
     */
    public boolean seedProjectAuthIfEmpty() {
        return this == PROJECT;
    }

    @JsonCreator
    public static ApiImportUploadType fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim();
        for (ApiImportUploadType t : values()) {
            if (t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
