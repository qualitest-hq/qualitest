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
 * 抽凭证：credentialApi 标明哪一口登录；托管头占位符（{{asset.*}} / {{flow.*}}）标明写入目标。
 * 抽取 from/expr 只活在登录流 extracts。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAuthConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 多套鉴权。数组顺序就是勾选顺序，未命中前缀时用下标 0。
     */
    @Builder.Default
    private List<ProjectAuthProfile> authProfiles = new ArrayList<>();

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

        /** 鉴权头值，如 Bearer {{asset.adminAuth.token}}。 */
        private String headerValueTemplate;

        /**
         * 本套发凭证的接口，通常是 POST /login。
         * 造流只对这一口按托管头占位符补 extracts。
         */
        private CredentialApi credentialApi;

        /** 本套预制接口：登录、注册、验证码等。 */
        @Builder.Default
        private List<PrefabricatedApi> apis = new ArrayList<>();
    }

    /**
     * 发凭证接口的 method + path。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredentialApi implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** HTTP 方法，如 POST。 */
        private String method;

        /** 接口路径，如 /login。 */
        private String path;
    }

    /**
     * 预制接口。业务字段与 test_project_api 同名同义，但不带库主键与项目归属。
     * JSON 对象列在模板里保持对象，种子时再序列化成库列字符串。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrefabricatedApi implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 作者期雪花 id（十进制字符串，与正式接口 JSON 同形）；
         * Apply 时 remap 为项目真实 test_project_api 主键。
         */
        private String testProjectApiId;

        /** 接口名称。 */
        private String apiName;

        /** 接口路径。 */
        private String apiPath;

        /** 分组名，可用点号表示多级，如 管理端.系统.登录。 */
        private String apiGroup;

        /** 协议，默认 http。 */
        private String protocolType;

        /** 接口状态，默认 normal。 */
        private String apiStatus;

        /** 接口详细描述。 */
        private String apiDescription;

        /** 请求配置（method、body.example 等）。 */
        private Object requestConfig;

        /** 请求头配置。 */
        private Object headers;

        /** Cookie 配置。 */
        private Object cookies;

        /** 响应配置。 */
        private Object responseConfig;

        /** 测试值层。 */
        private Object testValueConfig;

        /** 业务 code 白名单。 */
        private Object bizCodeConfig;

        /** 鉴权标签，预制口一般只写 mode（none / inherit）。 */
        private ApiAuthConfig authConfig;

        /** 造流提示，如 {hints:[...]}。 */
        private Object designHints;

        /**
         * 可选上传保护：1=种子到项目后导入跳过；0/缺省=不保护。
         */
        private Integer syncProtected;

        /** 前置操作脚本。 */
        private String preRequestScript;

        /** 后置操作脚本。 */
        private String postRequestScript;
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
}
