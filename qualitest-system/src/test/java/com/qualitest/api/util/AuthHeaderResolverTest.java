package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：鉴权头解析与托管头写入。
 * 边界：none / inherit / 显式头不覆盖 / profileManaged 刷新 / 项目配置空。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AuthHeaderResolverTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthHeaderResolverTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    /**
     * 前提：接口 mode=none。
     * 期望：skip，不补头。
     */
    @Test
    @Order(1)
    @DisplayName("none 不加头")
    void resolve_none_skips() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("none").build());
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(apiAuth, PROJECT_AUTH, "/api/account/auth/profile");
        assertTrue(resolved.skipped());
        AuthHeaderResolver.ApplyResult applied =
                AuthHeaderResolver.applyToHeaderRows(List.of(), resolved);
        assertFalse(applied.changed());
        assertTrue(applied.headers().isEmpty());
    }

    /**
     * 前提：inherit、路径 /api/、项目有双端模板。
     * 期望：clientBearer，值为 Bearer {{flow.token}}。
     */
    @Test
    @Order(2)
    @DisplayName("inherit 客户端路径补 Bearer token")
    void resolve_inherit_clientPath() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(apiAuth, PROJECT_AUTH, "/api/account/auth/profile");
        assertFalse(resolved.skipped());
        assertEquals("Authorization", resolved.name());
        assertEquals("Bearer {{flow.token}}", resolved.valueTemplate());
        assertEquals(ProjectAuthConfigSupport.PROFILE_CLIENT, resolved.profileId());
    }

    /**
     * 前提：缺头。
     * 期望：追加 profileManaged 行。
     */
    @Test
    @Order(3)
    @DisplayName("缺头时追加托管行")
    void apply_addsManagedHeader() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        PROJECT_AUTH,
                        "/api/orders");
        AuthHeaderResolver.ApplyResult applied =
                AuthHeaderResolver.applyToHeaderRows(new ArrayList<>(), resolved);
        assertTrue(applied.changed());
        assertEquals(1, applied.headers().size());
        Map<String, Object> row = applied.headers().get(0);
        assertEquals("Authorization", row.get("name"));
        assertEquals("Bearer {{flow.token}}", row.get("value"));
        assertTrue(AuthHeaderResolver.isProfileManaged(row));
    }

    /**
     * 前提：已有同名非托管头。
     * 期望：不覆盖。
     */
    @Test
    @Order(4)
    @DisplayName("显式头不被覆盖")
    void apply_keepsExplicitHeader() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        PROJECT_AUTH,
                        "/api/orders");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> explicit = new LinkedHashMap<>();
        explicit.put("_enabled", true);
        explicit.put("name", "Authorization");
        explicit.put("value", "Bearer {{flow.adminToken}}");
        rows.add(explicit);

        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(rows, resolved);
        assertFalse(applied.changed());
        assertEquals("Bearer {{flow.adminToken}}", applied.headers().get(0).get("value"));
        assertFalse(AuthHeaderResolver.isProfileManaged(applied.headers().get(0)));
    }

    /**
     * 前提：已有 profileManaged 旧模板。
     * 期望：按当前 Profile 刷新值。
     */
    @Test
    @Order(5)
    @DisplayName("托管头按当前配置刷新")
    void apply_refreshesManagedHeader() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        PROJECT_AUTH,
                        "/api/orders");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> managed = new LinkedHashMap<>();
        managed.put("_enabled", true);
        managed.put("name", "Authorization");
        managed.put("value", "Bearer {{flow.oldToken}}");
        managed.put(AuthHeaderResolver.PROFILE_MANAGED, true);
        rows.add(managed);

        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(rows, resolved);
        assertTrue(applied.changed());
        assertEquals("Bearer {{flow.token}}", applied.headers().get(0).get("value"));
        assertTrue(AuthHeaderResolver.isProfileManaged(applied.headers().get(0)));
    }

    /**
     * 前提：发送行无 Authorization。
     * 期望：追加托管头。
     */
    @Test
    @Order(6)
    @DisplayName("缺头时 apply 写入 Authorization")
    void apply_addsWhenSendRowsEmpty() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        PROJECT_AUTH,
                        "/system/user/list");
        AuthHeaderResolver.ApplyResult applied =
                AuthHeaderResolver.applyToHeaderRows(List.of(), resolved);
        assertTrue(applied.changed());
        assertEquals("Authorization", applied.headers().get(0).get("name"));
        assertEquals("Bearer {{flow.adminToken}}", applied.headers().get(0).get("value"));
    }

    /**
     * 前提：项目鉴权为空。
     * 期望：skip。
     */
    @Test
    @Order(7)
    @DisplayName("项目鉴权空则 skip")
    void resolve_emptyProjectAuth_skips() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        null,
                        "/api/orders");
        assertTrue(resolved.skipped());
    }
}
