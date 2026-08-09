package com.qualitest.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 接口鉴权标签。
 * <p>
 * 描述该接口是否免登录、是否继承项目鉴权配置，或是否使用本接口自定义鉴权头。
 * 由插件扫描上传后写入接口资产，供后续执行请求时决定是否加 Authorization。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAuthConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 免登录，不加鉴权头。 */
    public static final String MODE_NONE = "none";

    /**
     * 需要登录，按项目鉴权配置解析。
     */
    public static final String MODE_INHERIT = "inherit";

    /**
     * 本接口使用自定义鉴权头模板。
     */
    public static final String MODE_OVERRIDE = "override";

    /**
     * 鉴权模式：none（免登录）、inherit（继承项目配置）、override（本接口自定义）。
     */
    private String mode;

    /**
     * 项目鉴权配置 id。
     * 可空：为空时按接口路径匹配；有值则固定使用该配置。
     */
    private String authProfileId;
}
