package com.qualitest.file.storage;

import com.qualitest.common.exception.ServiceException;

/**
 * 文件存储后端类型。同一时刻只使用其中一种，由配置项 qualitest.storage.type 指定。
 */
public enum StorageType {

    /** 写入应用本机上传目录（qualitest.profile 下的 upload） */
    LOCAL("local"),

    /** 写入 RustFS（S3 兼容对象存储） */
    RUSTFS("rustfs"),

    /** 写入七牛云对象存储 */
    QINIU("qiniu");

    /** 接口与库内回传的存储标识字符串 */
    private final String code;

    StorageType(String code) {
        this.code = code;
    }

    /**
     * 返回对外存储标识（local / rustfs / qiniu）。
     */
    public String code() {
        return code;
    }

    /**
     * 将配置字符串解析为枚举。空值按 RUSTFS 处理；无法识别的值直接抛业务异常。
     *
     * @param raw 配置原文，如 LOCAL、RUSTFS、QINIU
     */
    public static StorageType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return RUSTFS;
        }
        try {
            return StorageType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("非法存储类型: " + raw + "（仅支持 LOCAL / RUSTFS / QINIU）");
        }
    }
}

