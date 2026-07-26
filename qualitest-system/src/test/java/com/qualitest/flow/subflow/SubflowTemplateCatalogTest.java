package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SubflowTemplateCatalog} 单元测试：平台内置子流模板目录与 flowOutputs 嵌入。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=SubflowTemplateCatalogTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowTemplateCatalogTest {

    private static final String OAUTH_TEMPLATE_ID = "tpl_oauth_client_credentials";
    private static final String LOGIN_BEARER_TEMPLATE_ID = "tpl_login_bearer";

    /**
     * listTemplates 应加载 classpath 内置模板目录。
     * 期望：列表非空；含 tpl_oauth_client_credentials。
     */
    @Test
    @Order(1)
    void listTemplates_loadsPlatformCatalog() {
        begin("listTemplates_loadsPlatformCatalog");
        var templates = SubflowTemplateCatalog.listTemplates();
        assertFalse(templates.isEmpty());
        assertTrue(templates.stream().anyMatch(t -> OAUTH_TEMPLATE_ID.equals(t.getString("templateId"))));
        log("templateCount=" + templates.size());
        end("listTemplates_loadsPlatformCatalog");
    }

    /**
     * findById 应解析已知 templateId；未知 id 或 null 返回 null。
     * 期望：OAuth 模板含 templateId 与 name。
     */
    @Test
    @Order(2)
    void findById_resolvesKnownTemplate() {
        begin("findById_resolvesKnownTemplate");
        JSONObject tpl = SubflowTemplateCatalog.findById(OAUTH_TEMPLATE_ID);
        assertNotNull(tpl);
        assertEquals(OAUTH_TEMPLATE_ID, tpl.getString("templateId"));
        assertNotNull(tpl.getString("name"));
        assertNull(SubflowTemplateCatalog.findById("tpl_not_exists"));
        assertNull(SubflowTemplateCatalog.findById(null));
        log("name=" + tpl.getString("name"));
        end("findById_resolvesKnownTemplate");
    }

    /**
     * loadGraphJson 应返回模板图骨架 JSON。
     * 期望：OAuth 模板含 http 节点；未知 templateId 返回 null。
     */
    @Test
    @Order(3)
    void loadGraphJson_oauthTemplate() {
        begin("loadGraphJson_oauthTemplate");
        String graph = SubflowTemplateCatalog.loadGraphJson(OAUTH_TEMPLATE_ID);
        assertNotNull(graph);
        assertTrue(graph.contains("\"type\": \"http\""));
        assertNull(SubflowTemplateCatalog.loadGraphJson("tpl_missing"));
        log("graphLength=" + graph.length());
        end("loadGraphJson_oauthTemplate");
    }

    /**
     * embedFlowOutputsIntoGraph 应将模板 outputs 写入 meta.flowOutputs。
     * 期望：嵌入后 flowOutputs 非空；空模板元数据时写入空数组。
     */
    @Test
    @Order(4)
    void embedFlowOutputsIntoGraph_writesMetaFlowOutputs() {
        begin("embedFlowOutputsIntoGraph_writesMetaFlowOutputs");
        JSONObject tpl = SubflowTemplateCatalog.findById(LOGIN_BEARER_TEMPLATE_ID);
        assertNotNull(tpl);

        String baseGraph = SubflowTemplateCatalog.loadGraphJson(LOGIN_BEARER_TEMPLATE_ID);
        assertNotNull(baseGraph);
        String embedded = SubflowTemplateCatalog.embedFlowOutputsIntoGraph(baseGraph, tpl);
        JSONObject graph = JSONObject.parseObject(embedded);
        JSONArray flowOutputs = graph.getJSONObject("meta").getJSONArray("flowOutputs");
        assertNotNull(flowOutputs);
        assertFalse(flowOutputs.isEmpty());

        JSONObject oauthTpl = SubflowTemplateCatalog.findById(OAUTH_TEMPLATE_ID);
        assertNotNull(oauthTpl);
        String oauthGraph = SubflowTemplateCatalog.embedFlowOutputsIntoGraph(
                SubflowTemplateCatalog.loadGraphJson(OAUTH_TEMPLATE_ID), oauthTpl);
        JSONArray oauthOutputs = JSONObject.parseObject(oauthGraph).getJSONObject("meta").getJSONArray("flowOutputs");
        assertFalse(oauthOutputs.isEmpty());

        String empty = SubflowTemplateCatalog.embedFlowOutputsIntoGraph("{\"meta\":{}}", null);
        assertTrue(JSONObject.parseObject(empty).getJSONObject("meta").getJSONArray("flowOutputs").isEmpty());
        log("outputs=" + flowOutputs.size());
        end("embedFlowOutputsIntoGraph_writesMetaFlowOutputs");
    }
}
