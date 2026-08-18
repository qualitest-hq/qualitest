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
 * 项目鉴权配置，存在 test_project.auth_config。
 * <p>
 * 按接口路径选一套 Profile：命中最长 pathPrefix 的那条；都没命中则用数组第一条。
 * 免登只看预制接口 authConfig.mode=none。
 * 登录口在同一份 authConfig 上带 loginHint，标明从响应哪抽取凭证、写入哪个 flow 变量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAuthConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 历史字段：曾经表示「未命中前缀时用哪条 Profile」。
     * 解析时把该条调到数组第一位，写出时删除。
     */
    private String defaultProfileId;

    /**
     * 多套鉴权。数组顺序就是勾选顺序，未命中前缀时用下标 0。
     */
    @Builder.Default
    private List<ProjectAuthProfile> authProfiles = new ArrayList<>();

    /**
     * 历史字段：曾经列出免登精确路径。
     * 解析时转成 path-only、mode=none 的预制接口，写出时删除。
     */
    @Builder.Default
    private List<String> anonymousPathExact = new ArrayList<>();

    /**
     * 历史字段：曾经列出免登路径前缀。解析时忽略，写出时删除。
     */
    @Builder.Default
    private List<String> anonymousPathPrefix = new ArrayList<>();

    /**
     * 一套鉴权：请求头模板 + 若干预制接口。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectAuthProfile implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Profile 唯一 id。从模板拷贝时新生成，不沿用模板主键。 */
        private String id;

        /** 展示名，一般取模板名称。 */
        private String name;

        /** 路径前缀匹配；空表示不按前缀区分。 */
        private Match match;

        /** 鉴权头名称，如 Authorization、Cookie。 */
        private String headerName;

        /** 鉴权头值，如 Bearer {{flow.token}}。 */
        private String headerValueTemplate;

        /** 本套预制接口：登录、注册、验证码等。 */
        @Builder.Default
        private List<PrefabricatedApi> apis = new ArrayList<>();

        /**
         * 历史字段：嵌套形态的头。
         * 解析时拍平到 headerName、headerValueTemplate，写出时删除。
         */
        private Header header;

        /**
         * 历史字段：整套 Profile 曾经挂的登录抽取。
         * 解析时挂到登录口 apis[].authConfig.loginHint，写出时删除。
         */
        private LoginHint loginHint;
    }

    /**
     * 预制接口。字段含义同项目接口资产，但不带库主键。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrefabricatedApi implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 接口名称。 */
        private String apiName;

        /** 接口路径。 */
        private String apiPath;

        /** 分组名，可用点号表示多级，如 系统.登录。 */
        private String apiGroup;

        /** 协议，默认 http。 */
        private String protocolType;

        /** 接口状态，默认 normal。 */
        private String apiStatus;

        /** 请求配置（method、body.example 等）。 */
        private Object requestConfig;

        /** 鉴权标签；登录口常为 mode=none 并带 loginHint。 */
        private ApiAuthConfig authConfig;

        /** 造流提示，如 {hints:[...]}。 */
        private Object designHints;
    }

    /**
     * 路径匹配规则：接口路径命中任一 pathPrefix 即选用本 Profile；多条命中取最长前缀。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Match implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 路径前缀列表，如 /api/、/system/。禁止单独写 /。 */
        private List<String> pathPrefix;
    }

    /**
     * 历史嵌套头：name + valueTemplate。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 头名称。 */
        private String name;

        /** 头值模板。 */
        private String valueTemplate;
    }

    /**
     * 登录响应里怎么抽出凭证。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginHint implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 写入 flow 的变量名，如 token、adminToken。 */
        private String flowKey;

        /**
         * 抽取来源：body、setCookie、header。
         * 未填且只有 extractJsonPath 时按 body 处理。
         */
        private String from;

        /**
         * 抽取表达式。body 时为 JSONPath；setCookie 时为 Cookie 名。
         */
        private String expr;

        /**
         * 历史字段：等价于 from=body 且 expr 取本值。写出时不再带。
         */
        private String extractJsonPath;
    }
}
