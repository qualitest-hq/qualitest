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
 * 边界：none / inherit / 显式头不覆盖 / profileManaged 刷新 / override / 项目配置空。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AuthHeaderResolverTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthHeaderResolverTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

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
     * 期望：clientBearer，值为 Bearer {{asset.clientAuth.token}}。
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
        assertEquals("Bearer {{asset.clientAuth.token}}", resolved.valueTemplate());
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
        assertEquals("Bearer {{asset.clientAuth.token}}", row.get("value"));
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
        explicit.put("value", "Bearer {{asset.adminAuth.token}}");
        rows.add(explicit);

        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(rows, resolved);
        assertFalse(applied.changed());
        assertEquals("Bearer {{asset.adminAuth.token}}", applied.headers().get(0).get("value"));
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
        assertEquals("Bearer {{asset.clientAuth.token}}", applied.headers().get(0).get("value"));
        assertTrue(AuthHeaderResolver.isProfileManaged(applied.headers().get(0)));
    }

    /**
     * 前提：托管头已与当前 Profile 模板一致。
     * 期望：changed=false，避免无意义刷新提案。
     */
    @Test
    @Order(6)
    @DisplayName("托管头已一致时不标变更")
    void apply_managedHeaderAlreadyCurrent_noChange() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        PROJECT_AUTH,
                        "/api/orders");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> managed = new LinkedHashMap<>();
        managed.put("_enabled", true);
        managed.put("name", "Authorization");
        managed.put("value", "Bearer {{asset.clientAuth.token}}");
        managed.put(AuthHeaderResolver.PROFILE_MANAGED, true);
        rows.add(managed);

        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(rows, resolved);
        assertFalse(applied.changed());
        assertEquals("Bearer {{asset.clientAuth.token}}", applied.headers().get(0).get("value"));
    }

    /**
     * 前提：发送行无 Authorization。
     * 期望：追加托管头。
     */
    @Test
    @Order(7)
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
        assertEquals("Bearer {{asset.adminAuth.token}}", applied.headers().get(0).get("value"));
    }

    /**
     * 前提：项目鉴权为空。
     * 期望：skip。
     */
    @Test
    @Order(8)
    @DisplayName("项目鉴权空则 skip")
    void resolve_emptyProjectAuth_skips() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        null,
                        "/api/orders");
        assertTrue(resolved.skipped());
    }

    /**
     * 前提：项目 Profile 托管 Cookie 头；接口 inherit。
     * 期望：补 Cookie: name={{asset…}}，与 Authorization 同一套 apply。
     */
    @Test
    @Order(9)
    @DisplayName("Cookie Profile 补托管 Cookie 头")
    void resolve_cookieProfile_appliesCookieHeader() {
        String projectAuth = """
                {
                  "authProfiles":[{
                    "id":"sessionCookie",
                    "name":"会话 Cookie",
                    "headerName":"Cookie",
                    "headerValueTemplate":"JSESSIONID={{asset.adminAuth.jsessionId}}"
                  }]
                }
                """;
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("inherit").build()),
                        projectAuth,
                        "/app/home");
        assertFalse(resolved.skipped());
        assertEquals("Cookie", resolved.name());
        assertEquals("JSESSIONID={{asset.adminAuth.jsessionId}}", resolved.valueTemplate());
        AuthHeaderResolver.ApplyResult applied =
                AuthHeaderResolver.applyToHeaderRows(List.of(), resolved);
        assertTrue(applied.changed());
        assertEquals(true, applied.headers().get(0).get(AuthHeaderResolver.PROFILE_MANAGED));
        assertEquals("Cookie", applied.headers().get(0).get("name"));
    }

    /**
     * 前提：mode=override 且配置了自定义头。
     * 期望：按接口模板补头，无 profileId。
     */
    @Test
    @Order(10)
    @DisplayName("override 按接口自定义头补全")
    void resolve_override_usesApiHeader() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder()
                .mode("override")
                .header(ApiAuthConfig.Header.builder()
                        .name("Authorization")
                        .valueTemplate("Bearer invalid-token")
                        .build())
                .build());
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(apiAuth, PROJECT_AUTH, "/api/account/auth/profile");
        assertFalse(resolved.skipped());
        assertEquals("Authorization", resolved.name());
        assertEquals("Bearer invalid-token", resolved.valueTemplate());
        assertEquals(null, resolved.profileId());
        AuthHeaderResolver.ApplyResult applied =
                AuthHeaderResolver.applyToHeaderRows(new ArrayList<>(), resolved);
        assertTrue(applied.changed());
        assertTrue(AuthHeaderResolver.isProfileManaged(applied.headers().get(0)));
    }

    /**
     * 前提：mode=override 但缺 header。
     * 期望：resolve skip（落库侧会拒绝，解析侧兜底不加头）。
     */
    @Test
    @Order(11)
    @DisplayName("override 缺 header 则 skip")
    void resolve_overrideWithoutHeader_skips() {
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(
                        "{\"mode\":\"override\"}", PROJECT_AUTH, "/api/orders");
        assertTrue(resolved.skipped());
    }

    /**
     * 前提：当前 JSON 含 credentialApi 与 /login none 口。
     * 期望：inherit 的 /login 不加 Bearer。
     */
    @Test
    @Order(12)
    @DisplayName("预制 none 口：inherit 的 /login 不加头")
    void resolve_prefabricatedNoneLogin_skips() {
        String projectAuth = """
                {"authProfiles":[{
                  "id":"defaultBearer","name":"Bearer",
                  "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                  "credentialApi":{"method":"POST","path":"/login"},
                  "apis":[{"apiPath":"/login","authConfig":{"mode":"none"},
                    "requestConfig":{"configVersion":1,"method":"POST"}}]
                }]}
                """;
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        AuthHeaderResolver.ResolvedAuthHeader resolved =
                AuthHeaderResolver.resolve(apiAuth, projectAuth, "/login");
        assertTrue(resolved.skipped());
    }
}
