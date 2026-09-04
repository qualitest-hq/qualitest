package com.qualitest.project.support.templatepack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.qualitest.project.domain.TestProjectTemplate;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目模板导入导出用的请求 / 响应 / 精简包模型。
 */
public final class ProjectTemplatePackModels {

    private ProjectTemplatePackModels() {
    }

    /**
     * 精简冷启动模板正文。
     * 只描述鉴权相关接口、素材口令、环境等短字段；不含预制测试流图。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlimTemplate {
        /** 模板显示名，必填 */
        private String templateName;
        private String remark;
        /** 启用状态，缺省按启用 */
        private Integer enableStatus;
        private Integer sortNum;
        /** 鉴权风格：bearer / session / header / none / custom */
        private String authStyle;
        /** 路径前缀列表，禁止单独 "/" */
        private List<String> pathPrefix;
        /** 登录凭证提取与素材入口提示 */
        private SlimCredential credential;
        /** 素材口令 map：入口名 → 字段对象（如 username/password） */
        private Map<String, Map<String, Object>> assets;
        /** 单个环境（与 envs 可并存） */
        private SlimEnv env;
        /** 多个环境 */
        private List<SlimEnv> envs;
        /** 鉴权相关接口列表，必填 */
        private List<SlimApi> apis;
        /** 生成时不确定的项（如 extract），导入时转成 warning */
        @JsonProperty("_uncertain")
        private List<String> uncertain;
        /** 若传入则忽略并 warning，不生成测试流 */
        private JsonNode flows;
    }

    /** 精简包里的凭证提示，写入 matchConfig.credential，并生成首个接口的 designHints。 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlimCredential {
        /** 素材入口名，如 adminAuth */
        private String asset;
        /** JSONPath 字符串，或 { from, expr } */
        private Object extract;
        /** token 落入素材的字段名；缺省可从 extract 末段推断 */
        private String tokenField;
        /** session 场景的 Cookie 名 */
        private String cookieName;
        /** header 鉴权时的请求头名 */
        private String headerName;
        /** header 鉴权时的值模板 */
        private String headerValueTemplate;
    }

    /** 精简环境：名称、baseUrl、简易变量 map。 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlimEnv {
        private String envName;
        private String envUrl;
        /** key → 默认值，展开成 envVariables */
        private Map<String, Object> variables;
    }

    /** 精简接口：展开成预制接口 PrefabricatedApi。 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlimApi {
        private String name;
        private String method;
        private String path;
        /** none / inherit 等 */
        private String authMode;
        private String apiGroup;
        /** json / form / none，缺省 json */
        private String bodyMode;
        private Map<String, Object> headers;
        private Map<String, Object> query;
        private Map<String, Object> body;
        /** 响应样例，用于推断 schema 与测值 */
        private Map<String, Object> response;
        /** 是否同步保护；缺省视为保护 */
        private Object syncProtected;
    }

    /** 导入请求体。 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImportRequest {
        /** true 时只校验预览、不写库 */
        private Boolean dryRun;
        /** true 时同名自定义模板更新而非报错 */
        private Boolean overwriteByName;
        /** 完整包或精简包 JSON 根对象 */
        private JsonNode template;
    }

    /** 校验 / 导入接口的响应。 */
    @Data
    public static class PackOpResult {
        /** 写入后的模板 id；dryRun 或仅校验时为空 */
        private Long testProjectTemplateId;
        /** full 或 slim */
        private String kind;
        /** 非致命提示（如忽略 flows、不确定项） */
        private List<String> warnings;
        /** 数量摘要：apiCount、assetCount、flowCount 等 */
        private Map<String, Object> expandedSummary;
        /** 展开后的字段预览，供管理端展示 */
        private Map<String, Object> preview;
    }

    /** 精简包展开器的中间结果。 */
    @Data
    public static class ExpandResult {
        /** 可入库的模板实体 */
        private TestProjectTemplate entity;
        private List<String> warnings;
        private Map<String, Object> expandedSummary = new LinkedHashMap<>();
        private Map<String, Object> preview = new LinkedHashMap<>();
    }
}
