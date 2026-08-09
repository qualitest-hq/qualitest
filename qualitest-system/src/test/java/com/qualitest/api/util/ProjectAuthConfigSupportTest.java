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
 * 边界：/api 与管理端前缀、禁止 match=/、空配置。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthConfigSupportTest {

    /**
     * 前提：双端默认模板。
     * 期望：非空，且 default 为管理端。
     */
    @Test
    @Order(1)
    @DisplayName("双端模板非空")
    void dualBearerTemplate_notEmpty() {
        ProjectAuthConfig cfg = ProjectAuthConfigSupport.dualBearerTemplate();
        assertFalse(ProjectAuthConfigSupport.isEmpty(cfg));
        assertEquals(ProjectAuthConfigSupport.PROFILE_ADMIN, cfg.getDefaultProfileId());
        assertEquals(2, cfg.getAuthProfiles().size());
    }

    /**
     * 前提：路径以 /api/ 开头。
     * 期望：clientBearer。
     */
    @Test
    @Order(2)
    @DisplayName("客户端路径命中")
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
     * 前提：管理端路径前缀。
     * 期望：adminBearer。
     */
    @Test
    @Order(3)
    @DisplayName("管理端路径命中")
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
     * 前提：未命中任何前缀。
     * 期望：使用 defaultProfileId（管理端）。
     */
    @Test
    @Order(4)
    @DisplayName("未命中走默认")
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
    @Order(5)
    @DisplayName("空配置")
    void emptyConfig() {
        assertTrue(ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.empty()));
        assertTrue(ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.parse(null)));
        assertEquals(null, ProjectAuthConfigSupport.resolveProfileId("/api/x", ProjectAuthConfigSupport.empty()));
    }

    /**
     * 前提：双端模板含 demo 匿名 path。
     * 期望：/login exact、/test-support/snapshot prefix 命中；业务 API 不命中。
     */
    @Test
    @Order(6)
    @DisplayName("匿名 path 命中")
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
     * 前提：已有 Profile 但匿名 path 列表为空。
     * 期望：fillAnonymousPathsIfAbsent 回填且返回 true；再调一次返回 false。
     */
    @Test
    @Order(7)
    @DisplayName("缺匿名 path 可回填")
    void fillAnonymousPathsIfAbsent_once() {
        ProjectAuthConfig cfg = ProjectAuthConfig.builder()
                .defaultProfileId(ProjectAuthConfigSupport.PROFILE_ADMIN)
                .authProfiles(ProjectAuthConfigSupport.dualBearerTemplate().getAuthProfiles())
                .build();
        assertTrue(ProjectAuthConfigSupport.fillAnonymousPathsIfAbsent(cfg));
        assertFalse(cfg.getAnonymousPathExact().isEmpty());
        assertFalse(ProjectAuthConfigSupport.fillAnonymousPathsIfAbsent(cfg));
    }
}
