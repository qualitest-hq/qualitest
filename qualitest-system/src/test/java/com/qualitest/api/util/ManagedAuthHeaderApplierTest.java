package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：ManagedAuthHeaderApplier——登录口不补/剥离托管 Bearer。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ManagedAuthHeaderApplierTest
 */
class ManagedAuthHeaderApplierTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            ProjectAuthConfigSupport.dualBearerTemplate());

    @Test
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
    @DisplayName("extracts 写出 token 时剥离误补托管头")
    void apply_tokenProducer_stripsManaged() {
        Map<String, Object> managed = new LinkedHashMap<>();
        managed.put("_enabled", true);
        managed.put("name", "Authorization");
        managed.put("value", "Bearer {{flow.token}}");
        managed.put(AuthHeaderResolver.PROFILE_MANAGED, true);

        Map<String, Object> data = new HashMap<>();
        data.put("headers", new ArrayList<>(List.of(managed)));
        data.put("extracts", List.of(Map.of(
                "name", "token",
                "scope", "flow",
                "expr", "$.data.token"
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
}
