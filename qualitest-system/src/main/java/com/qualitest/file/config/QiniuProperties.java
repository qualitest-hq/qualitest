package com.qualitest.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 七牛对象存储连接参数，对应 yml 前缀 qualitest.qiniu。
 * <p>
 * 仅当 qualitest.storage.type=QINIU 时使用；密钥不要提交到仓库，可用本机私有配置或环境变量注入。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qualitest.qiniu")
public class QiniuProperties {

    /** 七牛 AccessKey */
    private String accessKey = "";

    /** 七牛 SecretKey */
    private String secretKey = "";

    /** 空间名称 */
    private String bucket = "";

    /** 访问域名（可不带协议），例如 cdn.example.com */
    private String domain = "";

    /** 访问协议，默认 https:// */
    private String domainProtocol = "https://";

    /**
     * 存储区域简写：z0/z1/z2/na0/as0；留空则由 SDK 自动选择。
     */
    private String region = "";

    /**
     * 对象键可选统一前缀，可空。
     */
    private String source = "";

    /**
     * 上传所需关键项是否齐全。
     */
    public boolean isReady() {
        return notBlank(accessKey) && notBlank(secretKey) && notBlank(bucket) && notBlank(domain);
    }

    /**
     * 规范化协议字符串，保证以 :// 结尾。
     */
    public String resolveProtocol() {
        if (domainProtocol == null || domainProtocol.isBlank()) {
            return "https://";
        }
        return domainProtocol.endsWith("://") ? domainProtocol : domainProtocol + "://";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

