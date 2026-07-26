package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/** list_project_envs：列举项目环境 id/name/url，环境变量仅返回键名 */
@RequiredArgsConstructor
public class ListProjectEnvsTool implements QualitestTool {

    private final ITestProjectEnvService testProjectEnvService;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_PROJECT_ENVS.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        TestProjectEnv probe = new TestProjectEnv();
        probe.setTestProjectId(ctx.getTestProjectId());
        List<TestProjectEnv> envs = testProjectEnvService.selectTestProjectEnvList(probe);
        JSONArray items = new JSONArray();
        for (TestProjectEnv env : envs) {
            if (env == null) {
                continue;
            }
            if (env.getDelStatus() != null && env.getDelStatus() != 0) {
                continue;
            }
            if (env.getTestProjectId() == null || !env.getTestProjectId().equals(ctx.getTestProjectId())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("id", String.valueOf(env.getTestProjectEnvId()));
            item.put("name", env.getEnvName());
            item.put("envUrl", env.getEnvUrl());
            item.put("shareStatus", env.getShareStatus());
            item.put("sortNum", env.getSortNum() != null ? env.getSortNum() : 0);
            item.put("envVarKeys", extractEnvVarKeys(env.getEnvVariables()));
            items.add(item);
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }

    private static JSONArray extractEnvVarKeys(String envVariablesJson) {
        JSONArray keys = new JSONArray();
        if (envVariablesJson == null || envVariablesJson.isBlank()) {
            return keys;
        }
        try {
            Object parsed = JSON.parse(envVariablesJson.trim());
            if (parsed instanceof JSONArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject obj) {
                        String key = obj.getString("key");
                        if (key != null && !key.isBlank()) {
                            keys.add(key.trim());
                        }
                    }
                }
            } else if (parsed instanceof JSONObject obj) {
                for (String key : obj.keySet()) {
                    keys.add(key);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return keys;
    }
}
