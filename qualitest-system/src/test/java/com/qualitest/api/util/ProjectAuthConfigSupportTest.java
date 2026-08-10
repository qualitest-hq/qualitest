package com.qualitest.api.util;

import com.qualitest.api.model.ProjectAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：项目鉴权配置模板、判空、按路径解析 Profile。
 * 边界：通用种子 vs demo 双端、禁止 match=/、空配置。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthConfigSupportTest {

    /**
     * 前提：通用上传种子。
     * 期望：单 Profile、无匿名 path；任意路径回落到 defaultBearer。
     */
    @Test
    @Order(1)
    @DisplayName("通用种子：单套 Bearer、无匿名 path")
    void defaultBearerTemplate_conservative() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.defaultBearerTemplate();
        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertEquals(ProjectAuthConfigSupport.PROFILE_DEFAULT, cfg.getDefaultProfileId());
        assertEquals(1, cfg.getAuthProfiles().size());
        assertTrue(cfg.getAnonymousPathExact() == null || cfg.getAnonymousPathExact().isEmpty());
        assertTrue(cfg.getAnonymousPathPrefix() == null || cfg.getAnonymousPathPrefix().isEmpty());
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_DEFAULT,
                ProjectAuthConfigSupport.resolveProfileId("/api/account/auth/profile", cfg));
        assertEquals(
                ProjectAuthConfigSupport.PROFILE_DEFAULT,
                ProjectAuthConfigSupport.resolveProfileId("/system/user/list", cfg));
        assertFalse(ProjectAuthConfigSupport.matchesAnonymousPath("/login", cfg));
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
}
