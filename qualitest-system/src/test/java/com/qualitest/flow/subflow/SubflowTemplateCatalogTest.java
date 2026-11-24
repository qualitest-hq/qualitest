package com.qualitest.flow.subflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphSchemaVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 SubflowTemplateCatalog：内置模板目录、按 id 查找、图骨架规范化。
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
     * 前提：login_bearer / oauth 模板及其图骨架；另测空 meta。
     * 期望：normalize 写入当前 schemaVersion；不含 meta.flowOutputs。
     */
    @Test
    @Order(4)
    @DisplayName("规范化：写入 schemaVersion，不含 flowOutputs")
    void normalizeTemplateGraph_setsSchemaVersion() {
        JSONObject tpl = SubflowTemplateCatalog.findById(LOGIN_BEARER_TEMPLATE_ID);
        assertNotNull(tpl);

        String baseGraph = SubflowTemplateCatalog.loadGraphJson(LOGIN_BEARER_TEMPLATE_ID);
        assertNotNull(baseGraph);
        String normalized = SubflowTemplateCatalog.normalizeTemplateGraph(baseGraph, tpl);
        JSONObject graph = JSONObject.parseObject(normalized);
        assertNotNull(graph.getJSONObject("meta").getInteger("schemaVersion"));
        assertFalse(graph.getJSONObject("meta").containsKey("flowOutputs"));

        JSONObject oauthTpl = SubflowTemplateCatalog.findById(OAUTH_TEMPLATE_ID);
        assertNotNull(oauthTpl);
        String oauthGraph = SubflowTemplateCatalog.normalizeTemplateGraph(
                SubflowTemplateCatalog.loadGraphJson(OAUTH_TEMPLATE_ID), oauthTpl);
        assertFalse(JSONObject.parseObject(oauthGraph).getJSONObject("meta").containsKey("flowOutputs"));

        String empty = SubflowTemplateCatalog.normalizeTemplateGraph("{\"meta\":{}}", null);
        assertNotNull(JSONObject.parseObject(empty).getJSONObject("meta").getInteger("schemaVersion"));
    }

    /**
     * 前提：目录中每个 templateId 均有 classpath 图 JSON。
     * 期望：Flow 2.0 可调用子流骨架（subflow-entry/return、fallbackOut、in.* 入参、入口出边）。
     */
    @Test
    @Order(5)
    @DisplayName("模板图：全部符合 Flow 2.0 可调用子流契约")
    void everyTemplateGraph_matchesFlow2CallableSubflowShape() {
        for (JSONObject tpl : SubflowTemplateCatalog.listTemplates()) {
            String templateId = tpl.getString("templateId");
            assertNotNull(templateId, "templateId");

            String raw = SubflowTemplateCatalog.loadGraphJson(templateId);
            assertNotNull(raw, templateId + " graph missing");

            String normalized = SubflowTemplateCatalog.normalizeTemplateGraph(raw, tpl);
            JSONObject graph = JSONObject.parseObject(normalized);
            assertNotNull(graph);

            Integer schemaVersion = graph.getJSONObject("meta").getInteger("schemaVersion");
            assertEquals(GraphSchemaVersions.V2, schemaVersion, templateId + " schemaVersion");

            JSONArray nodes = graph.getJSONArray("nodes");
            assertNotNull(nodes);
            assertFalse(nodes.isEmpty(), templateId + " nodes empty");

            boolean hasSubflowEntry = false;
            boolean hasSubflowReturn = false;
            String entryNodeId = null;
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String type = node.getString("type");
                if ("subflow-entry".equals(type)) {
                    hasSubflowEntry = true;
                    entryNodeId = node.getString("id");
                    assertNotNull(node.getString("name"), templateId + " entry name");
                    JSONObject data = node.getJSONObject("data");
                    assertNotNull(data, templateId + " entry data");
                    assertNotNull(data.getJSONObject("fallbackOut"), templateId + " fallbackOut");
                }
                if ("subflow-return".equals(type)) {
                    hasSubflowReturn = true;
                    assertNotNull(node.getString("name"), templateId + " return name");
                    assertEquals("flow.out", node.getJSONObject("data").getString("from"),
                            templateId + " return from");
                }
            }
            assertTrue(hasSubflowEntry, templateId + " lacks subflow-entry");
            assertTrue(hasSubflowReturn, templateId + " lacks subflow-return");

            assertFalse(raw.contains("{{flow.username}}"), templateId);
            assertFalse(raw.contains("{{flow.password}}"), templateId);
            assertFalse(raw.contains("{{flow.refreshToken}}"), templateId);
            assertFalse(raw.contains("{{flow.userA}}"), templateId);

            assertTrue(raw.contains("fallbackOut"), templateId + " fallbackOut missing in source");

            JSONArray edges = graph.getJSONArray("edges");
            boolean wiredFromEntry = "e_entry".equals(entryNodeId);
            if (!wiredFromEntry && edges != null && entryNodeId != null) {
                for (int i = 0; i < edges.size(); i++) {
                    JSONObject edge = edges.getJSONObject(i);
                    if (entryNodeId.equals(edge.getString("source"))) {
                        wiredFromEntry = true;
                        break;
                    }
                }
            }
            assertTrue(wiredFromEntry, templateId + " entry not wired to first step");
        }
    }
}