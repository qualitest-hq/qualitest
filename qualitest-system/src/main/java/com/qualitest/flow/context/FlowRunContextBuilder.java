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
 * 将环境变量、素材库条目写入对应 scope；场景入参值树灌入 {@code in}；
 * 写入 testProjectId 与 externalHttpPermitted。
 * pausePermitted 不在此设置：场景开跑由项目 allowPause 写入，开放入站固定 false。
 */
public final class FlowRunContextBuilder {

    private FlowRunContextBuilder() {
    }

    /**
     * 组装单次 Run 运行时上下文（无场景入参种子、无项目 id，外联 HTTP 默认禁止）。
     */
    public static FlowRunContext build(TestProjectEnv env, String assetJson) {
        return build(env, assetJson, null, null, false);
    }

    /** 组装上下文并写入所属测试项目 id（外联默认禁止）。 */
    public static FlowRunContext build(TestProjectEnv env, String assetJson, Long testProjectId) {
        return build(env, assetJson, null, testProjectId, false);
    }

    /**
     * 组装上下文：指定项目 id，并控制是否允许外联 HTTP。
     */
    public static FlowRunContext build(TestProjectEnv env, String assetJson,
                                       Long testProjectId, boolean externalHttpPermitted) {
        return build(env, assetJson, null, testProjectId, externalHttpPermitted);
    }

    /**
     * 组装上下文：{@code inSeed} 灌 {@code ctx.in}；{@code flow} 保持空映射直至节点赋值。
     */
    public static FlowRunContext build(TestProjectEnv env, String assetJson,
                                       Map<String, Object> inSeed,
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
                .testProjectId(testProjectId)
                .externalHttpPermitted(externalHttpPermitted)
                .build();
    }

    /**
     * 按已解析场景组装：{@code inSeed} 灌 in。
     */
    public static FlowRunContext buildFromScenario(TestProjectEnv env, String assetJson,
                                                   ResolvedRunScenario scenario,
                                                   Long testProjectId, boolean externalHttpPermitted) {
        Map<String, Object> inSeed = scenario != null ? scenario.getInSeed() : null;
        return build(env, assetJson, inSeed, testProjectId, externalHttpPermitted);
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