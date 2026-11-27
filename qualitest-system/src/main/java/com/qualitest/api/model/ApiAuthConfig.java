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
 * {@code mode} 必填：none（免登录）、inherit（继承项目配置）、override（本接口自定义头）。
 * Web / MCP / IDEA 写入均须带 mode；运行期据此决定是否加托管 Authorization。
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
     * mode=override 时不使用。
     */
    private String authProfileId;

    /**
     * 本接口自定义鉴权头（仅 mode=override 时使用）。
     */
    private Header header;

    /**
     * 接口级自定义鉴权头模板。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 头名称，通常为 Authorization。 */
        private String name;

        /** 头值模板，如 Bearer {{asset.x.token}} / Bearer {{flow.token}} 或字面量。 */
        private String valueTemplate;
    }
}
