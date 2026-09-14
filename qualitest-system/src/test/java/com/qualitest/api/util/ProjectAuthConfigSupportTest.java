package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;
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
 * 测谁：项目鉴权配置当前形态、免登只认预制 none 口。
 * 边界：空配置 builtin、有 Profile 不再猜 /login；凭证目标在托管头。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthConfigSupportTest {

    /**
     * 前提：通用上传种子。
     * 期望：单 Profile 名「RuoYi Bearer」；写出 JSON 不含 loginHint；托管头 asset.adminAuth.token；登录口 none。
     */
    @Test
    @Order(1)
    @DisplayName("通用种子：RuoYi Bearer 三口、asset 头、无客户端 path")
    void ruoyiBearerTemplate_conservative() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.ruoyiBearerTemplate();

        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertEquals(1, cfg.getAuthProfiles().size());
        assertEquals("RuoYi Bearer", cfg.getAuthProfiles().get(0).getName());
        assertEquals("Authorization", cfg.getAuthProfiles().get(0).getHeaderName());
        assertEquals("Bearer {{asset.adminAuth.token}}",
                cfg.getAuthProfiles().get(0).getHeaderValueTemplate());
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("GET", "/captchaImage", cfg));
        assertFalse(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                "POST", "/api/account/auth/login", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_RUOYI,
                ProjectAuthConfigSupport.resolveProfileId("/system/user/list", cfg));
        PrefabricatedApi login = ProjectAuthConfigSupport.findPrefabricatedApi(cfg, "POST", "/login");
        assertNotNull(login);
        assertEquals("none", login.getAuthConfig().getMode());
        assertTrue(String.valueOf(login.getTestValueConfig()).contains("{{asset.adminAuth.username}}"));
        assertTrue(String.valueOf(login.getResponseConfig()).contains("token"));
        assertEquals("POST", cfg.getAuthProfiles().get(0).getCredentialApi().getMethod());
        assertEquals("/login", cfg.getAuthProfiles().get(0).getCredentialApi().getPath());
        String roundtripJson = ProjectAuthConfigSupport.toJson(cfg);
        assertFalse(roundtripJson.contains("loginHint"));
        ProjectAuthConfig roundtrip = ProjectAuthConfigSupport.parse(roundtripJson);
        assertEquals("Bearer {{asset.adminAuth.token}}",
                roundtrip.getAuthProfiles().get(0).getHeaderValueTemplate());
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
                  "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                  "apis":[]
                }]}
                """;
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(json);

        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertTrue(ProjectAuthConfigSupport.needsAuthTemplateHint(cfg));
        assertFalse(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
    }

    /**
     * 前提：当前结构双端 JSON（扁平头、credentialApi、asset 头、none 登录口）。
     * 期望：免登；写出 JSON 不含 loginHint；头模板为 asset。
     */
    @Test
    @Order(5)
    @DisplayName("parse 当前双端 JSON")
    void parse_currentDualJson() {
        String raw = """
                {"authProfiles":[
                  {"id":"adminBearer","name":"管理端 Bearer",
                   "match":{"pathPrefix":["/system/","/monitor/","/tool/","/web/"]},
                   "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                   "credentialApi":{"method":"POST","path":"/login"},
                   "apis":[{"apiPath":"/login","authConfig":{"mode":"none"},
                     "requestConfig":{"configVersion":1,"method":"POST"}}]},
                  {"id":"clientBearer","name":"客户端 Bearer","match":{"pathPrefix":["/api/"]},
                   "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.clientAuth.token}}",
                   "credentialApi":{"method":"POST","path":"/api/account/auth/login"},
                   "apis":[{"apiPath":"/api/account/auth/login","authConfig":{"mode":"none"},
                     "requestConfig":{"configVersion":1,"method":"POST"}}]}
                ]}
                """;
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(raw);

        assertEquals("adminBearer", cfg.getAuthProfiles().get(0).getId());
        assertEquals("Authorization", cfg.getAuthProfiles().get(0).getHeaderName());
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                "POST", "/api/account/auth/login", cfg));
        PrefabricatedApi adminLogin = ProjectAuthConfigSupport.findPrefabricatedApi(cfg, "POST", "/login");
        assertEquals("none", adminLogin.getAuthConfig().getMode());
        assertEquals("Bearer {{asset.adminAuth.token}}",
                cfg.getAuthProfiles().get(0).getHeaderValueTemplate());
        PrefabricatedApi clientLogin = ProjectAuthConfigSupport.findPrefabricatedApi(
                cfg, "POST", "/api/account/auth/login");
        assertNotNull(clientLogin);
        assertEquals("none", clientLogin.getAuthConfig().getMode());
        assertEquals("Bearer {{asset.clientAuth.token}}",
                cfg.getAuthProfiles().get(1).getHeaderValueTemplate());
        assertFalse(ProjectAuthConfigSupport.toJson(cfg).contains("loginHint"));
    }

    /**
     * 前提：当前结构写入 normalizeToJson。
     * 期望：落库含扁平头与 credentialApi，不含嵌套 header / loginHint。
     */
    @Test
    @Order(6)
    @DisplayName("normalize 只写出当前形态")
    void normalizeToJson_writesFlatShape() {
        String raw = """
                {"authProfiles":[
                  {"id":"p1","name":"x",
                   "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                   "credentialApi":{"method":"POST","path":"/login"},
                   "loginHint":{"flowKey":"token","from":"body","expr":"$.token"},
                   "apis":[{"apiPath":"/login","authConfig":{"mode":"none"},
                     "requestConfig":{"configVersion":1,"method":"POST"}}]}
                ]}
                """;
        String stored = ProjectAuthConfigSupport.normalizeToJson(raw);

        assertFalse(stored.contains("defaultProfileId"));
        assertFalse(stored.contains("anonymousPathExact"));
        assertFalse(stored.contains("\"header\":"));
        assertFalse(stored.contains("loginHint"));
        assertTrue(stored.contains("headerName"));
        assertTrue(stored.contains("\"apis\""));
        assertTrue(stored.contains("credentialApi"));
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(stored);
        assertEquals("Authorization", cfg.getAuthProfiles().get(0).getHeaderName());
        assertEquals("Bearer {{asset.adminAuth.token}}",
                cfg.getAuthProfiles().get(0).getHeaderValueTemplate());
    }

    /**
     * 前提：托管头含 asset 占位符。
     * 期望：primaryTarget 解析出 entry/field。
     */
    @Test
    @Order(7)
    @DisplayName("primaryTarget：asset 托管头")
    void primaryTarget_fromAssetHeader() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.ruoyiBearerTemplate();
        CredentialTarget target = CredentialTargetSupport.primaryTarget(cfg.getAuthProfiles().get(0));
        assertNotNull(target);
        assertTrue(target.isAsset());
        assertEquals("adminAuth", target.entryKey());
        assertEquals("token", target.fieldPath());
        assertEquals("asset.adminAuth.token", target.displayPath());
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
                   "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}"}
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
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.ruoyiBearerTemplate();
        assertTrue(ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth("POST", "/login", cfg));
        assertEquals(ApiAuthConfig.MODE_NONE,
                ProjectAuthConfigSupport.findPrefabricatedApi(cfg, "POST", "/login").getAuthConfig().getMode());
    }

    /**
     * 前提：预制口带描述、头、响应、测值、脚本。
     * 期望：normalize 写出后再 parse，这些字段还在；写出 JSON 不含 loginHint。
     */
    @Test
    @Order(12)
    @DisplayName("normalize：预制口同名字段透传")
    void normalizeToJson_keepsPrefabBusinessFields() {
        String raw = """
                {"authProfiles":[{
                  "id":"p1","name":"x",
                  "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                  "credentialApi":{"method":"POST","path":"/login"},
                  "loginHint":{"flowKey":"token","from":"body","expr":"$.token"},
                  "apis":[{
                    "apiName":"登录","apiPath":"/login","apiGroup":"管理端.系统.登录",
                    "protocolType":"http","apiStatus":"normal",
                    "apiDescription":"管理端登录",
                    "requestConfig":{"configVersion":1,"method":"POST"},
                    "headers":{"X-Client":"qualitest"},
                    "cookies":{"sid":"1"},
                    "responseConfig":{"configVersion":1,"responses":[{"id":"ok"}]},
                    "testValueConfig":{"bodyExample":{"username":"admin"}},
                    "bizCodeConfig":{"successValues":[200]},
                    "authConfig":{"mode":"none"},
                    "designHints":{"hints":["token 在 $.token"]},
                    "preRequestScript":"pre()",
                    "postRequestScript":"post()"
                  }]
                }]}
                """;

        String stored = ProjectAuthConfigSupport.normalizeToJson(raw);
        assertFalse(stored.contains("loginHint"));
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(stored);
        PrefabricatedApi api = cfg.getAuthProfiles().get(0).getApis().get(0);

        assertEquals("管理端登录", api.getApiDescription());
        assertEquals("pre()", api.getPreRequestScript());
        assertEquals("post()", api.getPostRequestScript());
        assertTrue(String.valueOf(api.getHeaders()).contains("X-Client"));
        assertTrue(String.valueOf(api.getCookies()).contains("sid"));
        assertTrue(String.valueOf(api.getResponseConfig()).contains("ok"));
        assertTrue(String.valueOf(api.getTestValueConfig()).contains("admin"));
        assertTrue(String.valueOf(api.getBizCodeConfig()).contains("200"));
        assertTrue(String.valueOf(api.getDesignHints()).contains("$.token"));
        assertEquals("/login", cfg.getAuthProfiles().get(0).getCredentialApi().getPath());
    }

    /**
     * 前提：Profile 托管头 + credentialApi=POST /login。
     * 期望：登录口能找 Profile，注册口没有。
     */
    @Test
    @Order(13)
    @DisplayName("findCredentialProfile：只认 credentialApi")
    void findCredentialProfile_onlyCredentialApi() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.ruoyiBearerTemplate();

        assertNotNull(ProjectAuthConfigSupport.findCredentialProfile(cfg, "POST", "/login"));
        assertEquals("asset.adminAuth.token",
                CredentialTargetSupport.primaryTarget(
                        ProjectAuthConfigSupport.findCredentialProfile(cfg, "POST", "/login")).displayPath());
        assertNull(ProjectAuthConfigSupport.findCredentialProfile(cfg, "POST", "/register"));
        assertNull(ProjectAuthConfigSupport.findCredentialProfile(cfg, "GET", "/captchaImage"));
    }

    /**
     * 前提：管理端在前的双端夹具。
     * 期望：两端 primaryTarget 不同；写出 JSON 无 loginHint。
     */
    @Test
    @Order(14)
    @DisplayName("双端 Profile 各有 asset 目标，apis 不含 hint")
    void dualProfiles_assetTargetOnProfileNotApis() {
        String stored = ProjectAuthConfigSupport.toJson(AuthProfileTestFixtures.adminThenClient());
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(stored);

        assertTrue(stored.contains("credentialApi"));
        assertFalse(stored.contains("loginHint"));
        CredentialTarget admin = CredentialTargetSupport.primaryTarget(
                ProjectAuthConfigSupport.findCredentialProfile(cfg, "POST", "/login"));
        CredentialTarget client = CredentialTargetSupport.primaryTarget(
                ProjectAuthConfigSupport.findCredentialProfile(cfg, "POST", "/api/account/auth/login"));
        assertEquals("asset.adminAuth.token", admin.displayPath());
        assertEquals("asset.clientAuth.token", client.displayPath());
    }
}
