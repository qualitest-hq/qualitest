package com.qualitest.api.util;

import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：项目鉴权配置模板、判空、按路径解析 Profile、写入规范化。
 * 边界：通用种子 vs demo 双端、禁止 match=/、空配置、normalize 校验。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthConfigSupportTest {

    /**
     * 前提：通用上传种子。
     * 期望：单 Profile；含内置免登 path（/login 等）；业务路径仍回落 defaultBearer。
     */
    @Test
    @Order(1)
    @DisplayName("通用种子：单套 Bearer、含内置免登 path")
    void defaultBearerTemplate_conservative() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.defaultBearerTemplate();
        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertEquals(ProjectAuthConfigSupport.PROFILE_DEFAULT, cfg.getDefaultProfileId());
        assertEquals(1, cfg.getAuthProfiles().size());
        assertTrue(cfg.getAnonymousPathExact().contains("/login"));
        assertTrue(cfg.getAnonymousPathExact().contains("/api/account/auth/login"));
        assertTrue(cfg.getAnonymousPathPrefix() == null || cfg.getAnonymousPathPrefix().isEmpty());
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_DEFAULT,
                ProjectAuthConfigSupport.resolveProfileId("/api/account/auth/profile", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_DEFAULT,
                ProjectAuthConfigSupport.resolveProfileId("/system/user/list", cfg));
        assertTrue(ProjectAuthConfigSupport.matchesAnonymousPath("/login", cfg));
        assertTrue(ProjectAuthConfigSupport.matchesBuiltinAnonymousAuthPath("/login"));
        assertTrue(ProjectAuthConfigSupport.matchesBuiltinAnonymousAuthPath("/api/account/auth/register/"));
        assertFalse(ProjectAuthConfigSupport.matchesBuiltinAnonymousAuthPath("/api/account/auth/profile"));
    }

    /**
     * 前提：demo 双端参考模板。
     * 期望：非空，且 default 为管理端。
     */
    @Test
    @Order(2)
    @DisplayName("双端参考模板非空")
    void dualBearerTemplate_notEmpty() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.dualBearerTemplate();
        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertEquals(ProjectAuthConfigSupport.PROFILE_ADMIN, cfg.getDefaultProfileId());
        assertEquals(2, cfg.getAuthProfiles().size());
    }

    /**
     * 前提：双端模板，路径以 /api/ 开头。
     * 期望：clientBearer。
     */
    @Test
    @Order(3)
    @DisplayName("双端：客户端路径命中")
    void resolveProfileId_clientApi() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.dualBearerTemplate();
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_CLIENT,
                ProjectAuthConfigSupport.resolveProfileId("/api/account/auth/profile", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_CLIENT,
                ProjectAuthConfigSupport.resolveProfileId("api/login", cfg));
    }

    /**
     * 前提：双端模板，管理端路径前缀。
     * 期望：adminBearer。
     */
    @Test
    @Order(4)
    @DisplayName("双端：管理端路径命中")
    void resolveProfileId_adminPaths() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.dualBearerTemplate();
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_ADMIN,
                ProjectAuthConfigSupport.resolveProfileId("/web/account/list", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_ADMIN,
                ProjectAuthConfigSupport.resolveProfileId("/system/user/list", cfg));
    }

    /**
     * 前提：双端模板，未命中任何前缀。
     * 期望：使用 defaultProfileId（管理端）。
     */
    @Test
    @Order(5)
    @DisplayName("双端：未命中走默认")
    void resolveProfileId_fallbackDefault() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.dualBearerTemplate();
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_ADMIN,
                ProjectAuthConfigSupport.resolveProfileId("/other/ping", cfg));
    }

    /**
     * 前提：空配置。
     * 期望：isEmpty 为 true，resolve 返回 null。
     */
    @Test
    @Order(6)
    @DisplayName("空配置")
    void emptyConfig() {
        assertTrue(ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.empty()));
        assertTrue(ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.parse(null)));
        assertEquals(null, ProjectAuthConfigSupport.resolveProfileId("/api/x", ProjectAuthConfigSupport.empty()));
    }

    /**
     * 前提：双端参考模板含 demo 匿名 path。
     * 期望：/login exact、/test-support/snapshot prefix 命中；业务 API 不命中。
     */
    @Test
    @Order(7)
    @DisplayName("双端参考：匿名 path 命中")
    void matchesAnonymousPath_exactAndPrefix() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.dualBearerTemplate();
        assertTrue(cfg.getAnonymousPathExact().contains("/login"));
        assertTrue(ProjectAuthConfigSupport.matchesAnonymousPath("/login", cfg));
        assertTrue(ProjectAuthConfigSupport.matchesAnonymousPath("/captchaImage", cfg));
        assertTrue(ProjectAuthConfigSupport.matchesAnonymousPath("/api/account/auth/login", cfg));
        assertTrue(ProjectAuthConfigSupport.matchesAnonymousPath("/test-support/snapshot", cfg));
        assertTrue(ProjectAuthConfigSupport.matchesAnonymousPath("/v3/api-docs", cfg));
        assertFalse(ProjectAuthConfigSupport.matchesAnonymousPath("/api/account/auth/profile", cfg));
        assertFalse(ProjectAuthConfigSupport.matchesAnonymousPath("/system/user/list", cfg));
    }

    /**
     * 前提：种子 loginHint 使用 from+expr。
     * 期望：resolve 读出 body + JSONPath；旧 extractJsonPath  alone 可回退。
     */
    @Test
    @Order(8)
    @DisplayName("loginHint：from+expr 与旧 extractJsonPath 兼容")
    void resolveLoginExtract_fromExpr_andLegacy() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.defaultBearerTemplate();
        var hint = cfg.getAuthProfiles().get(0).getLoginHint();
        assertEquals("body", ProjectAuthConfigSupport.resolveLoginExtractFrom(hint));
        assertEquals("$.token", ProjectAuthConfigSupport.resolveLoginExtractExpr(hint));

        var legacy = ProjectAuthConfig.LoginHint.builder()
                .flowKey("token")
                .extractJsonPath("$.data.token")
                .build();
        assertEquals("body", ProjectAuthConfigSupport.resolveLoginExtractFrom(legacy));
        assertEquals("$.data.token", ProjectAuthConfigSupport.resolveLoginExtractExpr(legacy));

        var cookie = ProjectAuthConfig.LoginHint.builder()
                .flowKey("sid")
                .from("setCookie")
                .expr("JSESSIONID")
                .build();
        assertEquals("setCookie", ProjectAuthConfigSupport.resolveLoginExtractFrom(cookie));
        assertEquals("JSESSIONID", ProjectAuthConfigSupport.resolveLoginExtractExpr(cookie));
    }

    /**
     * 前提：双端模板 JSON。
     * 期望：normalizeToJson 保留两套 Profile 与 default=adminBearer。
     */
    @Test
    @Order(9)
    @DisplayName("normalize：合法双端模板可落库")
    void normalizeToJson_dualOk() {
        String json = ProjectAuthConfigSupport.toJson(ProjectAuthConfigSupport.dualBearerTemplate());
        String stored = ProjectAuthConfigSupport.normalizeToJson(json);
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.parse(stored);
        assertEquals(ProjectAuthConfigSupport.PROFILE_ADMIN, cfg.getDefaultProfileId());
        assertEquals(2, cfg.getAuthProfiles().size());
        assertTrue(cfg.getAnonymousPathExact().contains("/login"));
    }

    /**
     * 前提：pathPrefix 为 "/"。
     * 期望：ServiceException，文案含禁止。
     */
    @Test
    @Order(10)
    @DisplayName("normalize：禁止 pathPrefix=/")
    void normalizeToJson_rejectsRootPrefix() {
        String raw = """
                {"defaultProfileId":"p1","authProfiles":[
                  {"id":"p1","name":"x","match":{"pathPrefix":["/"]},
                   "header":{"name":"Authorization","valueTemplate":"Bearer {{flow.token}}"}}
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
    @Order(11)
    @DisplayName("normalize：拒绝重复 Profile id")
    void normalizeToJson_rejectsDuplicateId() {
        String raw = """
                {"authProfiles":[
                  {"id":"same","header":{"name":"Authorization","valueTemplate":"Bearer a"}},
                  {"id":"same","header":{"name":"Authorization","valueTemplate":"Bearer b"}}
                ]}
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ProjectAuthConfigSupport.normalizeToJson(raw));
        assertTrue(ex.getMessage().contains("重复"));
    }

    /**
     * 前提：defaultProfileId 不在 profiles 中。
     * 期望：ServiceException。
     */
    @Test
    @Order(12)
    @DisplayName("normalize：defaultProfileId 必须存在")
    void normalizeToJson_rejectsMissingDefault() {
        String raw = """
                {"defaultProfileId":"missing","authProfiles":[
                  {"id":"p1","header":{"name":"Authorization","valueTemplate":"Bearer {{flow.token}}"}}
                ]}
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ProjectAuthConfigSupport.normalizeToJson(raw));
        assertTrue(ex.getMessage().contains("defaultProfileId"));
    }

    /**
     * 前提：空白 / 无 profiles。
     * 期望：落库 EMPTY_JSON，isEmpty 为 true。
     */
    @Test
    @Order(13)
    @DisplayName("normalize：空配置落 {}")
    void normalizeToJson_empty() {
        assertEquals(ProjectAuthConfigSupport.EMPTY_JSON, ProjectAuthConfigSupport.normalizeToJson(""));
        assertEquals(ProjectAuthConfigSupport.EMPTY_JSON, ProjectAuthConfigSupport.normalizeToJson("{}"));
        assertTrue(ProjectAuthConfigSupport.isEmpty(
                ProjectAuthConfigSupport.parse(ProjectAuthConfigSupport.normalizeToJson("{\"authProfiles\":[]}"))));
    }
}
