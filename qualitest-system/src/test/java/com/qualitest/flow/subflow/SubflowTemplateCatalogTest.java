package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 SubflowTemplateCatalog：内置模板目录、按 id 查找、图骨架与 flowOutputs 嵌入。
 * 边界：classpath 模板；未知 templateId / null 元数据；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubflowTemplateCatalogTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowTemplateCatalogTest {

    private static final String OAUTH_TEMPLATE_ID = "tpl_oauth_client_credentials";
    private static final String LOGIN_BEARER_TEMPLATE_ID = "tpl_login_bearer";

    /**
     * 前提：classpath 内置模板目录可用。
     * 期望：列表非空；含 tpl_oauth_client_credentials。
     */
    @Test
    @Order(1)
    @DisplayName("目录：加载平台内置模板列表")
    void listTemplates_loadsPlatformCatalog() {
        var templates = SubflowTemplateCatalog.listTemplates();
        assertFalse(templates.isEmpty());
        assertTrue(templates.stream().anyMatch(t -> OAUTH_TEMPLATE_ID.equals(t.getString("templateId"))));
    }

    /**
     * 前提：已知 OAuth templateId；另测未知 id 与 null。
     * 期望：返回含 templateId/name；未知与 null 为 null。
     */
    @Test
    @Order(2)
    @DisplayName("查找：已知 id 命中，未知/null 返回 null")
    void findById_resolvesKnownTemplate() {
        JSONObject tpl = SubflowTemplateCatalog.findById(OAUTH_TEMPLATE_ID);
        assertNotNull(tpl);
        assertEquals(OAUTH_TEMPLATE_ID, tpl.getString("templateId"));
        assertNotNull(tpl.getString("name"));
        assertNull(SubflowTemplateCatalog.findById("tpl_not_exists"));
        assertNull(SubflowTemplateCatalog.findById(null));
    }

    /**
     * 前提：加载 OAuth 模板图；另测缺失 id。
     * 期望：JSON 含 http 节点；缺失 id 返回 null。
     */
    @Test
    @Order(3)
    @DisplayName("加载：OAuth 模板图含 http 节点")
    void loadGraphJson_oauthTemplate() {
        String graph = SubflowTemplateCatalog.loadGraphJson(OAUTH_TEMPLATE_ID);
        assertNotNull(graph);
        assertTrue(graph.contains("\"type\": \"http\""));
        assertNull(SubflowTemplateCatalog.loadGraphJson("tpl_missing"));
    }

    /**
     * 前提：login_bearer / oauth 模板及其图骨架；另测 null 元数据。
     * 期望：meta.flowOutputs 非空；null 元数据写入空数组。
     */
    @Test
    @Order(4)
    @DisplayName("嵌入：meta.flowOutputs 写入图骨架")
    void embedFlowOutputsIntoGraph_writesMetaFlowOutputs() {
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
    }
}
