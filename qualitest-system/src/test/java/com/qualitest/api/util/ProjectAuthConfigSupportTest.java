package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：项目鉴权配置新形态、旧 JSON 读兼容、免登只认预制 none 口。
 * 边界：空配置 builtin、有 Profile 不再猜 /login、defaultProfile 调到第一、忽略旧 prefix。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthConfigSupportTest {

    /**
     * 前提：通用上传种子。
     * 期望：单 Profile 名「默认 Bearer」；含 POST /login none + loginHint；不含客户端 path。
     */
    @Test
    @Order(1)
    @DisplayName("通用种子：默认 Bearer 三口、无客户端 path")
    void defaultBearerTemplate_conservative() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.defaultBearerTemplate();

        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertEquals(1, cfg.getAuthProfiles().size());
        assertEquals("默认 Bearer", cfg.getAuthProfiles().get(0).getName());
        assertEquals("Authorization", cfg.getAuthProfiles().get(0).getHeaderName());
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("GET", "/captchaImage", cfg));
        assertFalse(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                "POST", "/api/account/auth/login", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_DEFAULT,
                ProjectAuthConfigSupport.resolveProfileId("/system/user/list", cfg));
        PrefabricatedApi login = ProjectAuthConfigSupport.findPrefabricatedApi(cfg, "POST", "/login");
        assertNotNull(login);
        assertEquals("token", login.getAuthConfig().getLoginHint().getFlowKey());
        assertEquals("$.token", login.getAuthConfig().getLoginHint().getExpr());
    }

    /**
     * 前提：管理端在前 + 客户端夹具。
     * 期望：/api/ 命中客户端，/system/ 命中管理端，未命中走第一条（管理端）。
     */
    @Test
    @Order(2)
    @DisplayName("双端夹具：前缀命中与第一条兜底")
    void adminThenClient_prefixAndFirstFallback() {
        ProjectAuthConfig cfg = AuthProfileTestFixtures.adminThenClient();

        assertEquals(2, cfg.getAuthProfiles().size());
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_ADMIN,
                ProjectAuthConfigSupport.resolveProfileId("/other/ping", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_CLIENT,
                ProjectAuthConfigSupport.resolveProfileId("/api/account/auth/profile", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_ADMIN,
                ProjectAuthConfigSupport.resolveProfileId("/web/account/list", cfg));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                "POST", "/api/account/auth/login", cfg));
        assertFalse(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("GET", "/system/user/list", cfg));
    }

    /**
     * 前提：空配置。
     * 期望：isEmpty；resolve 返回 null；/login 仍走 builtin。
     */
    @Test
    @Order(3)
    @DisplayName("空配置才用 builtin /login")
    void emptyConfig_usesBuiltin() {
        assertTrue(ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.empty()));
        assertTrue(ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.parse(null)));
        assertNull(ProjectAuthConfigSupport.resolveProfileId("/api/x", ProjectAuthConfigSupport.empty()));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                "POST", "/login", ProjectAuthConfigSupport.empty()));
        assertTrue(ProjectAuthConfigSupport.matchesBuiltinAnonymousAuthPath("/api/account/auth/register/"));
        assertFalse(ProjectAuthConfigSupport.matchesBuiltinAnonymousAuthPath("/api/account/auth/profile"));
    }

    /**
     * 前提：有 Profile、apis 为空。
     * 期望：/login inherit 不再免登；needsAuthTemplateHint 为 true。
     */
    @Test
    @Order(4)
    @DisplayName("有 Profile 且 apis 空：不再猜 /login")
    void profilesWithoutApis_doNotGuessLogin() {
        String json = """
                {"authProfiles":[{
                  "id":"p1","name":"Bearer",
                  "headerName":"Authorization","headerValueTemplate":"Bearer {{flow.token}}",
                  "apis":[]
                }]}
                """;
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(json);

        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertTrue(ProjectAuthConfigSupport.needsAuthTemplateHint(cfg));
        assertFalse(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
    }

    /**
     * 前提：旧 JSON（嵌套 header、Profile loginHint、anonymousPathExact、defaultProfileId=admin）。
     * 期望：拍平头；admin 调到第一；/login 与客户端 login 成为 none 口并带 hint；忽略 prefix。
     */
    @Test
    @Order(5)
    @DisplayName("parse 读兼容旧双端 JSON")
    void parse_migratesLegacyDualJson() {
        String raw = """
                {"defaultProfileId":"adminBearer","authProfiles":[
                  {"id":"clientBearer","name":"客户端 Bearer","match":{"pathPrefix":["/api/"]},
                   "header":{"name":"Authorization","valueTemplate":"Bearer {{flow.token}}"},
                   "loginHint":{"flowKey":"token","from":"body","expr":"$.data.token"}},
                  {"id":"adminBearer","name":"管理端 Bearer",
                   "match":{"pathPrefix":["/system/","/monitor/","/tool/","/web/"]},
                   "header":{"name":"Authorization","valueTemplate":"Bearer {{flow.adminToken}}"},
                   "loginHint":{"flowKey":"adminToken","from":"body","expr":"$.token"}}
                ],"anonymousPathExact":["/login","/register","/captchaImage",
                  "/api/account/auth/login","/api/account/auth/register"],
                 "anonymousPathPrefix":["/test-support/","/swagger-ui"]}
                """;
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(raw);

        assertEquals("adminBearer", cfg.getAuthProfiles().get(0).getId());
        assertEquals("Authorization", cfg.getAuthProfiles().get(0).getHeaderName());
        assertNull(cfg.getDefaultProfileId());
        assertNull(cfg.getAnonymousPathExact());
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                "POST", "/api/account/auth/login", cfg));
        assertFalse(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("GET", "/test-support/snapshot", cfg));
        PrefabricatedApi adminLogin = ProjectAuthConfigSupport.findPrefabricatedApi(cfg, "POST", "/login");
        assertEquals("adminToken", adminLogin.getAuthConfig().getLoginHint().getFlowKey());
        PrefabricatedApi clientLogin = ProjectAuthConfigSupport.findPrefabricatedApi(
                cfg, "POST", "/api/account/auth/login");
        assertEquals("$.data.token", clientLogin.getAuthConfig().getLoginHint().getExpr());
    }

    /**
     * 前提：旧 JSON 写入 normalizeToJson。
     * 期望：落库无 defaultProfileId / anonymousPath / 嵌套 header。
     */
    @Test
    @Order(6)
    @DisplayName("normalize 只写出新形态")
    void normalizeToJson_writesFlatShape() {
        String raw = """
                {"defaultProfileId":"p1","authProfiles":[
                  {"id":"p1","name":"x",
                   "header":{"name":"Authorization","valueTemplate":"Bearer {{flow.token}}"},
                   "loginHint":{"flowKey":"token","from":"body","expr":"$.token"}}
                ],"anonymousPathExact":["/login"]}
                """;
        String stored = ProjectAuthConfigSupport.normalizeToJson(raw);

        assertFalse(stored.contains("defaultProfileId"));
        assertFalse(stored.contains("anonymousPathExact"));
        assertFalse(stored.contains("anonymousPathPrefix"));
        assertFalse(stored.contains("\"header\":"));
        assertTrue(stored.contains("headerName"));
        assertTrue(stored.contains("\"apis\""));
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(stored);
        assertEquals("Authorization", cfg.getAuthProfiles().get(0).getHeaderName());
    }

    /**
     * 前提：loginHint 使用 from+expr；旧 extractJsonPath。
     * 期望：resolve 读出 body + JSONPath。
     */
    @Test
    @Order(7)
    @DisplayName("loginHint：from+expr 与旧 extractJsonPath 兼容")
    void resolveLoginExtract_fromExpr_andLegacy() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.defaultBearerTemplate();
        var hint = ProjectAuthConfigSupport.findLoginHint(cfg, "POST", "/login");
        assertEquals("body", ProjectAuthConfigSupport.resolveLoginExtractFrom(hint));
        assertEquals("$.token", ProjectAuthConfigSupport.resolveLoginExtractExpr(hint));

        var legacy = ProjectAuthConfig.LoginHint.builder()
                .flowKey("token")
                .extractJsonPath("$.data.token")
                .build();
        assertEquals("body", ProjectAuthConfigSupport.resolveLoginExtractFrom(legacy));
        assertEquals("$.data.token", ProjectAuthConfigSupport.resolveLoginExtractExpr(legacy));
    }

    /**
     * 前提：pathPrefix 为 "/"。
     * 期望：ServiceException，文案含禁止。
     */
    @Test
    @Order(8)
    @DisplayName("normalize：禁止 pathPrefix=/")
    void normalizeToJson_rejectsRootPrefix() {
        String raw = """
                {"authProfiles":[
                  {"id":"p1","name":"x","match":{"pathPrefix":["/"]},
                   "headerName":"Authorization","headerValueTemplate":"Bearer {{flow.token}}"}
                ]}
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ProjectAuthConfigSupport.normalizeToJson(raw));
        assertTrue(ex.getMessage().contains("禁止"));
    }

    /**
     * 前提：两个 Profile 同 id。
     * 期望：ServiceException 含「重复」。
     */
    @Test
    @Order(9)
    @DisplayName("normalize：拒绝重复 Profile id")
    void normalizeToJson_rejectsDuplicateId() {
        String raw = """
                {"authProfiles":[
                  {"id":"same","headerName":"Authorization","headerValueTemplate":"Bearer a"},
                  {"id":"same","headerName":"Authorization","headerValueTemplate":"Bearer b"}
                ]}
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ProjectAuthConfigSupport.normalizeToJson(raw));
        assertTrue(ex.getMessage().contains("重复"));
    }

    /**
     * 前提：空白 / 无 profiles。
     * 期望：落库 EMPTY_JSON。
     */
    @Test
    @Order(10)
    @DisplayName("normalize：空配置落 {}")
    void normalizeToJson_empty() {
        assertEquals(ProjectAuthConfigSupport.EMPTY_JSON, ProjectAuthConfigSupport.normalizeToJson(""));
        assertEquals(ProjectAuthConfigSupport.EMPTY_JSON, ProjectAuthConfigSupport.normalizeToJson("{}"));
        assertTrue(ProjectAuthConfigSupport.isEmpty(
                ProjectAuthConfigSupport.parse(ProjectAuthConfigSupport.normalizeToJson("{\"authProfiles\":[]}"))));
    }

    /**
     * 前提：inherit 的 /login，项目有 Profile 且 apis 含该口 mode=none。
     * 期望：应免登。
     */
    @Test
    @Order(11)
    @DisplayName("预制 none 口：/login 免登")
    void noneApi_skipsLogin() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.defaultBearerTemplate();
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertEquals(ApiAuthConfig.MODE_NONE,
                ProjectAuthConfigSupport.findPrefabricatedApi(cfg, "POST", "/login").getAuthConfig().getMode());
    }
}
