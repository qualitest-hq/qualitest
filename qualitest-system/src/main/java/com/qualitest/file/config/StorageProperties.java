package com.qualitest.file.config;

import com.qualitest.file.storage.StorageType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储策略配置，对应 yml 前缀 qualitest.storage。
 * <p>
 * type 决定实际写入哪一种后端，同一时刻只能选一种。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qualitest.storage")
public class StorageProperties {

    /**
     * 存储类型：LOCAL（本机盘）、RUSTFS（S3 兼容）、QINIU（七牛）。默认 RUSTFS。
     */
    private String type = "RUSTFS";

    /**
     * 解析为枚举；非法值会抛业务异常。
     */
    public StorageType resolvedType() {
        return StorageType.from(type);
    }
}

