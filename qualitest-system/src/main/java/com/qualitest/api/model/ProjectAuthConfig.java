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
     * 登录响应中抽取 token 的提示。
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

        /** 从登录响应取 token 的 JSONPath。 */
        private String extractJsonPath;
    }
}
