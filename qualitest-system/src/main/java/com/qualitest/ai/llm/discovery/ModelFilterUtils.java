package com.qualitest.ai.llm.discovery;

import com.qualitest.ai.llm.template.ProviderTemplate;

import java.util.List;

/**
 * 模板 modelFilters 通配符匹配：支持 includePatterns 白名单与 excludePatterns 黑名单。
 */
final class ModelFilterUtils {

    private ModelFilterUtils() {
    }

    /**
     * 判断 modelId 是否通过模板过滤规则；无 filters 时全部放行。
     */
    static boolean matches(String modelId, ProviderTemplate template) {
        if (template == null || template.getModelFilters() == null) {
            return true;
        }
        ProviderTemplate.ModelFilters filters = template.getModelFilters();
        List<String> includes = filters.getIncludePatterns();
        if (includes != null && !includes.isEmpty()) {
            boolean any = includes.stream().anyMatch(p -> globMatch(modelId, p));
            if (!any) {
                return false;
            }
        }
        List<String> excludes = filters.getExcludePatterns();
        if (excludes != null) {
            return excludes.stream().noneMatch(p -> globMatch(modelId, p));
        }
        return true;
    }

    /** 将 glob 模式（* 通配）转为正则并匹配 */
    private static boolean globMatch(String text, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return text.matches(regex);
    }
}
