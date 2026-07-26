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
 * 将环境变量、素材库条目、场景 flow 初值写入对应 scope，供占位符与节点 Handler 读取。
 */
public final class FlowRunContextBuilder {

    private FlowRunContextBuilder() {
    }

    /**
     * 组装单次 Run 运行时上下文。
     *
     * @param env         测试项目环境（含 envUrl / envVariables）
     * @param assetJson   项目 asset_variables JSON 文本
     * @param flowSeed    场景 flow 初值
     */
    public static FlowRunContext build(TestProjectEnv env, String assetJson, Map<String, Object> flowSeed) {
        return build(env, assetJson, flowSeed, null);
    }

    public static FlowRunContext build(TestProjectEnv env, String assetJson, Map<String, Object> flowSeed,
                                       Long testProjectId) {
        return build(env, assetJson, flowSeed, testProjectId, true);
    }

    public static FlowRunContext build(TestProjectEnv env, String assetJson, Map<String, Object> flowSeed,
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

        Map<String, Object> flow = new HashMap<>();
        if (flowSeed != null) {
            flow.putAll(flowSeed);
        }

        return FlowRunContext.builder()
                .env(envMap)
                .asset(assetMap)
                .flow(flow)
                .testProjectId(testProjectId)
                .externalHttpPermitted(externalHttpPermitted)
                .runSession(new com.qualitest.flow.session.FlowRunSession())
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
