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
 * 测 SubflowTemplateCatalog：内置模板目录、按 id 查找、图骨架与 flowOutputs 嵌入。
 * 边界：未知 templateId / null 元数据；存量 begin/end 保留。
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
    void listTemplates_loadsPlatformCatalog() {
        begin("listTemplates_loadsPlatformCatalog");
        var templates = SubflowTemplateCatalog.listTemplates();
        assertFalse(templates.isEmpty());
        assertTrue(templates.stream().anyMatch(t -> OAUTH_TEMPLATE_ID.equals(t.getString("templateId"))));
        log("templateCount=" + templates.size());
        end("listTemplates_loadsPlatformCatalog");
    }

    /**
     * 前提：已知 OAuth templateId；另测未知 id 与 null。
     * 期望：返回含 templateId/name；未知与 null 为 null。
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
     * 前提：加载 OAuth 模板图；另测缺失 id。
     * 期望：JSON 含 http 节点；缺失 id 返回 null。
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
     * 前提：login_bearer / oauth 模板及其图骨架；另测 null 元数据。
     * 期望：meta.flowOutputs 非空；null 元数据写入空数组。
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
