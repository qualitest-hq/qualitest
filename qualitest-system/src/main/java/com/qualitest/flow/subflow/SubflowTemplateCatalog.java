package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPromptResources;
import com.qualitest.flow.model.GraphSchemaVersions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台内置子流模板目录（classpath 资源，非数据库表）。
 * <p>
 * 元数据：{@code ai/subflow-templates.json}（templateId、名称、inputs/outputs 说明）。<br>
 * 图骨架：{@code ai/subflow-templates/{templateId}.graph.json}。
 * <p>
 * 内置模板：tpl_login_bearer（Bearer 登录）、tpl_login_captcha（取验证码图后外联打码再登录）、
 * tpl_login_captcha_manual（取验证码图后人工填码再登录）、tpl_oauth_client_credentials、
 * tpl_oauth_refresh、tpl_dual_login。
 * 用户可在画布「从平台模板创建」为项目内测试流，再在主流程 subflow 节点引用。
 */
public final class SubflowTemplateCatalog {

    private static final String RESOURCE = "ai/subflow-templates.json";

    private SubflowTemplateCatalog() {
    }

    /** 返回全部平台模板元数据列表 */
    public static List<JSONObject> listTemplates() {
        try {
            String json = FlowDesignPromptResources.loadText(RESOURCE);
            JSONArray arr = JSON.parseArray(json);
            if (arr == null || arr.isEmpty()) {
                return List.of();
            }
            List<JSONObject> out = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = arr.getJSONObject(i);
                if (item != null) {
                    out.add(item);
                }
            }
            return List.copyOf(out);
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 按 templateId（如 tpl_oauth_client_credentials）查找模板元数据 */
    public static JSONObject findById(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        for (JSONObject tpl : listTemplates()) {
            if (templateId.equals(tpl.getString("templateId"))) {
                return tpl;
            }
        }
        return null;
    }

    /** 加载模板对应的 graph_json 文本，不存在时返回 null */
    public static String loadGraphJson(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        String path = "ai/subflow-templates/" + templateId.trim() + ".graph.json";
        try {
            return FlowDesignPromptResources.loadText(path);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 把模板元数据中的 outputs 写入 graph_json.meta.flowOutputs。
     */
    public static String embedFlowOutputsIntoGraph(String graphJson, JSONObject template) {
        if (graphJson == null || graphJson.isBlank()) {
            return graphJson;
        }
        JSONObject graph = JSON.parseObject(graphJson);
        if (graph == null) {
            return graphJson;
        }
        JSONObject meta = graph.getJSONObject("meta");
        if (meta == null) {
            meta = new JSONObject();
            graph.put("meta", meta);
        }
        JSONArray flowOutputs = new JSONArray();
        if (template != null && template.getJSONArray("outputs") != null) {
            for (int i = 0; i < template.getJSONArray("outputs").size(); i++) {
                JSONObject output = template.getJSONArray("outputs").getJSONObject(i);
                if (output == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("name", output.getString("name"));
                if (output.containsKey("description")) {
                    item.put("description", output.getString("description"));
                }
                flowOutputs.add(item);
            }
        }
        meta.put("flowOutputs", flowOutputs);
        meta.put("schemaVersion", GraphSchemaVersions.CURRENT);
        return graph.toJSONString();
    }
}
