package com.qualitest.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目级鉴权配置。
 * <p>
 * 定义多套 Bearer（或其他头模板），按接口路径前缀匹配；未命中时用 defaultProfileId。
 * 可选 {@code anonymousPathExact} / {@code anonymousPathPrefix} 覆盖无注解白名单路径。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAuthConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 未命中任何 pathPrefix 时使用的配置 id。
     */
    private String defaultProfileId;

    /**
     * 多套鉴权配置。
     */
    @Builder.Default
    private List<ProjectAuthProfile> authProfiles = new ArrayList<>();

    /**
     * 免登录路径（精确匹配，规范化后全等），如 /login、/captchaImage。
     * 命中则接口 auth.mode=none，不补 Bearer。
     */
    @Builder.Default
    private List<String> anonymousPathExact = new ArrayList<>();

    /**
     * 免登录路径前缀（规范化后按段前缀匹配），如 /test-support/、/swagger-ui。
     */
    @Builder.Default
    private List<String> anonymousPathPrefix = new ArrayList<>();

    /**
     * 单套鉴权配置（如客户端 Bearer、管理端 Bearer）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectAuthProfile implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 配置唯一 id，如 clientBearer。 */
        private String id;

        /** 展示名。 */
        private String name;

        /** 路径匹配规则。 */
        private Match match;

        /** 写入请求的鉴权头。 */
        private Header header;

        /** 造流/登录时抽取 token 的提示。 */
        private LoginHint loginHint;
    }

    /**
     * 路径匹配：命中任一 pathPrefix 即选用本 Profile；多命中取最长前缀。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Match implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private List<String> pathPrefix;
    }

    /**
     * 鉴权请求头模板。
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

        /** 头值模板，如 Bearer {{flow.token}}。 */
        private String valueTemplate;
    }

    /**
     * 登录响应中抽取凭证的提示（与节点 extracts 同形：from + expr）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginHint implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 写入 flow 上下文的变量名，如 token / adminToken。 */
        private String flowKey;

        /**
         * 抽取来源，与 extracts.from 对齐：{@code body} / {@code setCookie} / {@code header}。
         * 空且仅有旧字段 {@link #extractJsonPath} 时按 body 读。
         */
        private String from;

        /**
         * 抽取表达式：body 时为 JSONPath；setCookie 时为 Cookie 名。
         */
        private String expr;

        /**
         * 旧字段：等价于 {@code from=body} + {@code expr}。读路径兼容；新写入请用 from/expr。
         */
        private String extractJsonPath;
    }
}
