package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：ManagedAuthHeaderApplier——登录口不补/剥离托管 Bearer；双端节点各写各的头。
 * 边界：登录 path、token 生产者剥离、同图 /api 与 /system 互不覆盖。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ManagedAuthHeaderApplierTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManagedAuthHeaderApplierTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

    @Test
    @Order(1)
    @DisplayName("登录 path 不补托管头")
    void apply_loginPath_noManagedHeader() {
        Map<String, Object> data = new HashMap<>();
        data.put("headers", new ArrayList<>());
        List<String> warnings = new ArrayList<>();
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        boolean changed = ManagedAuthHeaderApplier.applyToNodeData(
                data, apiAuth, PROJECT_AUTH, "/login", "管理端登录", warnings);
        assertFalse(changed);
        assertTrue(((List<?>) data.get("headers")).isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("extracts 写出凭证目标时剥离误补托管头")
    void apply_tokenProducer_stripsManaged() {
        Map<String, Object> managed = new LinkedHashMap<>();
        managed.put("_enabled", true);
        managed.put("name", "Authorization");
        managed.put("value", "Bearer {{asset.clientAuth.token}}");
        managed.put(AuthHeaderResolver.PROFILE_MANAGED, true);

        Map<String, Object> data = new HashMap<>();
        data.put("headers", new ArrayList<>(List.of(managed)));
        data.put("extracts", List.of(Map.of(
                "name", "token",
                "scope", "asset",
                "entryKey", "clientAuth",
                "fieldPath", "token",
                "expr", "$.data.token",
                "from", "body"
        )));
        List<String> warnings = new ArrayList<>();
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        boolean changed = ManagedAuthHeaderApplier.applyToNodeData(
                data, apiAuth, PROJECT_AUTH, "/api/account/auth/profile", "登录", warnings);
        assertTrue(changed);
        assertTrue(((List<?>) data.get("headers")).isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith(AuthDesignWarningCodes.LOGIN_NO_BEARER));
    }

    /**
     * 前提：双端模板；/api 与 /system 两个 inherit 节点各自补头。
     * 期望：客户端 / 管理端各写 asset 模板，互不覆盖。
     */
    @Test
    @Order(3)
    @DisplayName("同图双端节点各写各的托管头")
    void apply_dualPath_keepsSeparateTemplates() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        Map<String, Object> client = new HashMap<>();
        client.put("headers", new ArrayList<>());
        Map<String, Object> admin = new HashMap<>();
        admin.put("headers", new ArrayList<>());
        List<String> warnings = new ArrayList<>();

        boolean clientChanged = ManagedAuthHeaderApplier.applyToNodeData(
                client, apiAuth, PROJECT_AUTH, "/api/orders", "客户端下单", warnings);
        boolean adminChanged = ManagedAuthHeaderApplier.applyToNodeData(
                admin, apiAuth, PROJECT_AUTH, "/system/user/list", "管理端用户", warnings);

        assertTrue(clientChanged);
        assertTrue(adminChanged);
        assertEquals("Bearer {{asset.clientAuth.token}}", headerValue(client));
        assertEquals("Bearer {{asset.adminAuth.token}}", headerValue(admin));
    }

    @SuppressWarnings("unchecked")
    private static String headerValue(Map<String, Object> data) {
        List<Map<String, Object>> headers = (List<Map<String, Object>>) data.get("headers");
        return String.valueOf(headers.get(0).get("value"));
    }
}
