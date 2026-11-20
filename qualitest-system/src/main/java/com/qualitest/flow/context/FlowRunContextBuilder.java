package com.qualitest.flow.context;

import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.support.TestProjectVariableEntrySupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装单次 Run 的 {@link FlowRunContext}。
 * <p>
 * 把环境变量、素材库、in 初值写入对应作用域；{@code flow}/{@code out} 开跑为空，
 * 供占位符解析与节点 Handler 读写。
 */
public final class FlowRunContextBuilder {

    private FlowRunContextBuilder() {
    }

    /**
     * 组装单次 Run 运行时上下文（无入参初值）。
     */
    public static FlowRunContext build(TestProjectEnv env, String assetJson) {
        return build(env, assetJson, null, null, true);
    }

    public static FlowRunContext build(TestProjectEnv env, String assetJson, Long testProjectId) {
        return build(env, assetJson, null, testProjectId, true);
    }

    public static FlowRunContext build(TestProjectEnv env, String assetJson, Long testProjectId,
                                       boolean externalHttpPermitted) {
        return build(env, assetJson, null, testProjectId, externalHttpPermitted);
    }

    /**
     * 组装单次 Run 运行时上下文。
     *
     * @param inSeed                 入参初值，写入 {@code in}；可空
     * @param testProjectId          所属项目 id
     * @param externalHttpPermitted  是否允许节点发外联 HTTP
     */
    public static FlowRunContext build(TestProjectEnv env, String assetJson, Map<String, Object> inSeed,
                                       Long testProjectId, boolean externalHttpPermitted) {
        Map<String, Object> envMap = new HashMap<>();
        if (env != null) {
            List<TestProjectAsset> envEntries = TestProjectVariableEntrySupport.parseEntries(env.getEnvVariables());
            envMap.putAll(entriesToScopeMap(envEntries));
            String baseUrl = EnvUrlSupport.resolveEnvBaseUrlForRequest(env.getEnvUrl());
            if (!baseUrl.isEmpty()) {
                envMap.put("baseUrl", baseUrl);
            }
        }

        List<TestProjectAsset> assetEntries = TestProjectVariableEntrySupport.parseEntries(assetJson);
        Map<String, Object> assetMap = entriesToScopeMap(assetEntries);

        Map<String, Object> in = new HashMap<>();
        if (inSeed != null) {
            in.putAll(inSeed);
        }

        return FlowRunContext.builder()
                .env(envMap)
                .asset(assetMap)
                .flow(new HashMap<>())
                .in(in)
                .out(new HashMap<>())
                .testProjectId(testProjectId)
                .externalHttpPermitted(externalHttpPermitted)
                .build();
    }

    /**
     * 将变量条目列表转为 key → 业务值 Map，供 {@code {{env.*}}} / {@code {{asset.*}}} 取值。
     */
    static Map<String, Object> entriesToScopeMap(List<TestProjectAsset> entries) {
        Map<String, Object> map = new HashMap<>();
        if (entries == null) {
            return map;
        }
        for (TestProjectAsset entry : entries) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            Map<String, Object> assets = entry.getAssets();
            if (assets == null || assets.isEmpty()) {
                continue;
            }
            Object inner = assets.get(entry.getKey());
            if (inner != null) {
                map.put(entry.getKey(), inner);
            }
        }
        return map;
    }
}
