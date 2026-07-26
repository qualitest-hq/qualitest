package com.qualitest.ai.tools.apidesign;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI API 助手已注册工具名枚举。
 */
public enum ApiDesignToolNames {

    /** 读取当前接口结构、约束、测值预览、脚本摘要 */
    GET_API_DESIGN_CONTEXT("get_api_design_context"),

    /** 按 id 读取某个接口详情（通常用于查看其他接口） */
    GET_API_DETAIL("get_api_detail"),

    /** 列出项目环境及环境变量键名（不含值） */
    LIST_PROJECT_ENVS("list_project_envs"),

    /** 列出素材库变量 key 与子字段名（不含明文值） */
    LIST_ASSET_VARIABLES("list_asset_variables"),

    /** 提交结构化修改建议（不写库） */
    SUBMIT_API_DESIGN_PATCH("submit_api_design_patch");

    private final String id;

    ApiDesignToolNames(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** 全部工具 id 集合 */
    public static Set<String> allToolIds() {
        return Arrays.stream(values()).map(ApiDesignToolNames::getId).collect(Collectors.toSet());
    }
}
