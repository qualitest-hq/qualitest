package com.qualitest.project.support.templatepack;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ExpandResult;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精简包展开器单测。
 * 覆盖：Bearer 展开无流、flows/_uncertain 仅 warning、非法 pathPrefix、session credential、
 * 展开后再走完整包往返、tokenField 末段推断。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectTemplateSlimExpanderTest {

    private final ProjectTemplateSlimExpander expander = new ProjectTemplateSlimExpander();
    private final ProjectTemplateFullPackCodec fullPackCodec = new ProjectTemplateFullPackCodec();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @Order(1)
    @DisplayName("Bearer 展开：有 API/素材/环境，无测试流")
    void expand_bearer_noFlows() throws Exception {
        // 前提：标准 Bearer slim
        SlimTemplate slim = mapper.readValue(bearerJson(), SlimTemplate.class);

        ExpandResult result = expander.expand(slim);

        // 期望：接口与素材落库字段就绪，flows 为空
        assertNotNull(result.getEntity());
        assertEquals("Demo Bearer", result.getEntity().getTemplateName());
        assertEquals("[]", result.getEntity().getTemplateFlows());
        JSONArray apis = JSONUtil.parseArray(result.getEntity().getTemplateApis());
        assertEquals(2, apis.size());
        assertEquals(1, apis.getJSONObject(0).getInt("syncProtected"));
        JSONArray params = JSONUtil.parseArray(result.getEntity().getTemplateParams());
        assertEquals(1, params.size());
        assertEquals("adminAuth", params.getJSONObject(0).getStr("name"));
        JSONArray envs = JSONUtil.parseArray(result.getEntity().getTemplateEnvs());
        assertEquals(1, envs.size());
        assertEquals(0, result.getExpandedSummary().get("flowCount"));
        assertNotNull(result.getEntity().getMatchConfig());
        assertTrue(result.getEntity().getMatchConfig().contains("authStyle"));
    }

    @Test
    @Order(2)
    @DisplayName("传入 flows 与 _uncertain 仅 warning")
    void expand_flowsAndUncertain_warnOnly() throws Exception {
        // 前提：带 flows 与 _uncertain
        String json = """
                {
                  "templateName": "Warn Demo",
                  "authStyle": "bearer",
                  "pathPrefix": ["/api/"],
                  "_uncertain": ["extract"],
                  "flows": [{"name": "登录"}],
                  "apis": [{"name": "登录", "method": "POST", "path": "/login", "authMode": "none"}]
                }
                """;
        SlimTemplate slim = mapper.readValue(json, SlimTemplate.class);

        ExpandResult result = expander.expand(slim);

        // 期望：不写流，warnings 含 flows 与 uncertain
        assertEquals("[]", result.getEntity().getTemplateFlows());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("flows")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("extract")));
    }

    @Test
    @Order(3)
    @DisplayName("pathPrefix 为 / 时失败")
    void expand_pathPrefixRoot_fails() throws Exception {
        // 前提：pathPrefix 单独 /
        String json = """
                {
                  "templateName": "Bad",
                  "authStyle": "bearer",
                  "pathPrefix": ["/"],
                  "apis": [{"name": "登录", "method": "POST", "path": "/login"}]
                }
                """;
        SlimTemplate slim = mapper.readValue(json, SlimTemplate.class);

        // 期望：抛业务异常
        assertThrows(ServiceException.class, () -> expander.expand(slim));
    }

    @Test
    @Order(4)
    @DisplayName("Session credential 写入 matchConfig")
    void expand_session_credentialInMatch() throws Exception {
        // 前提：session + cookieName
        String json = """
                {
                  "templateName": "Demo Session",
                  "authStyle": "session",
                  "credential": {
                    "asset": "adminAuth",
                    "cookieName": "Admin-Token",
                    "extract": {"from": "header", "expr": "Set-Cookie"}
                  },
                  "apis": [{"name": "登录", "method": "POST", "path": "/login", "authMode": "none"}]
                }
                """;
        SlimTemplate slim = mapper.readValue(json, SlimTemplate.class);

        ExpandResult result = expander.expand(slim);

        // 期望：matchConfig 含 authStyle 与 cookieName
        JSONObject match = JSONUtil.parseObj(result.getEntity().getMatchConfig());
        assertEquals("session", match.getStr("authStyle"));
        assertEquals("Admin-Token", match.getJSONObject("credential").getStr("cookieName"));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("pathPrefix")));
    }

    @Test
    @Order(5)
    @DisplayName("展开后完整包往返保留名称与路径")
    void expand_thenFullPackRoundTrip_keepsNamePath() throws Exception {
        // 前提：Bearer 展开后走完整包编解码
        SlimTemplate slim = mapper.readValue(bearerJson(), SlimTemplate.class);
        TestProjectTemplate entity = expander.expand(slim).getEntity();

        Map<String, Object> pack = fullPackCodec.toPack(entity);
        TestProjectTemplate back = fullPackCodec.fromPack(mapper.valueToTree(pack));

        // 期望：关键字段仍在，仍无流
        assertEquals("Demo Bearer", back.getTemplateName());
        assertEquals("[]", back.getTemplateFlows());
        JSONArray apis = JSONUtil.parseArray(back.getTemplateApis());
        assertFalse(apis.isEmpty());
        assertEquals("/login", apis.getJSONObject(0).getStr("apiPath"));
        assertEquals(1, pack.get("formatVersion"));
    }

    @Test
    @Order(6)
    @DisplayName("tokenField 末段推断")
    void lastPathSegment_fromExtract() {
        // 前提：嵌套 JSONPath
        assertEquals("accessToken", ProjectTemplateSlimExpander.lastPathSegment("$.data.accessToken"));
        assertEquals("token", ProjectTemplateSlimExpander.lastPathSegment("$.token"));
        assertEquals("$.token", ProjectTemplateSlimExpander.extractExpr("$.token"));
        assertEquals("Set-Cookie", ProjectTemplateSlimExpander.extractExpr(Map.of("from", "header", "expr", "Set-Cookie")));
    }

    private static String bearerJson() {
        return """
                {
                  "templateName": "Demo Bearer",
                  "authStyle": "bearer",
                  "pathPrefix": ["/api/"],
                  "credential": {"asset": "adminAuth", "extract": "$.token"},
                  "assets": {"adminAuth": {"username": "admin", "password": "admin123"}},
                  "env": {"envUrl": "http://127.0.0.1:8800", "variables": {"clientId": "demo"}},
                  "apis": [
                    {
                      "name": "登录",
                      "method": "POST",
                      "path": "/login",
                      "authMode": "none",
                      "bodyMode": "json",
                      "headers": {"Content-Type": "application/json"},
                      "body": {
                        "username": "{{asset.adminAuth.username}}",
                        "password": "{{asset.adminAuth.password}}"
                      },
                      "response": {"code": 200, "token": "eyJ"}
                    },
                    {
                      "name": "获取用户信息",
                      "method": "GET",
                      "path": "/getInfo",
                      "authMode": "inherit",
                      "response": {"code": 200}
                    }
                  ]
                }
                """;
    }
}
