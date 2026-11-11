package com.qualitest.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RustFS（S3 兼容）连接参数，对应 yml 前缀 qualitest.rustfs。
 * <p>
 * 仅当 qualitest.storage.type=RUSTFS 时使用；此时须 enabled=true 且 endpoint 可达，否则上传会失败。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qualitest.rustfs")
public class RustfsProperties {

    /**
     * 是否创建 S3 客户端 Bean。为 false 时即使 type=RUSTFS 也无法上传。
     */
    private boolean enabled = false;

    /** S3 API 地址，例如 http://127.0.0.1:9000 */
    private String endpoint = "http://127.0.0.1:9000";

    /** Access Key */
    private String accessKey = "rustfsadmin";

    /** Secret Key */
    private String secretKey = "rustfsadmin";

    /** 桶名；启动时会尝试探测或创建 */
    private String bucket = "qualitest";

    /** 区域名，传给 S3 SDK，RustFS 一般可用 us-east-1 */
    private String region = "us-east-1";

    /**
     * 浏览器或客户端访问对象时使用的 URL 前缀，例如 http://127.0.0.1:9000/qualitest。
     * 为空时退化为 endpoint/bucket。
     */
    private String publicBaseUrl;
}

