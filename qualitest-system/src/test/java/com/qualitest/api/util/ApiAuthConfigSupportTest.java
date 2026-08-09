package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：鉴权标签转落库 JSON 的校验与序列化。
 * 边界：未声明 auth、合法 none/inherit、非法 mode。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiAuthConfigSupportTest {

    /**
     * 前提：上传包未带 auth，或 mode 为空。
     * 期望：返回 null，表示不要覆盖库中已有值。
     */
    @Test
    @Order(1)
    @DisplayName("省略 auth 不落库")
    void toStorageJson_nullWhenAuthOmitted() {
        assertNull(ApiAuthConfigSupport.toStorageJson(null));
        assertNull(ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("  ").build()));
    }

    /**
     * 前提：mode=none。
     * 期望：产出的 JSON 含 mode=none。
     */
    @Test
    @Order(2)
    @DisplayName("none 可落库")
    void toStorageJson_noneMode() {
        String json = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("none").build());
        assertTrue(json.contains("\"mode\":\"none\""));
    }

    /**
     * 前提：mode=inherit 且带 authProfileId。
     * 期望：JSON 同时含 mode 与 profileId。
     */
    @Test
    @Order(3)
    @DisplayName("inherit 可带 profileId")
    void toStorageJson_inheritWithProfile() {
        String json = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder()
                .mode("inherit")
                .authProfileId("clientBearer")
                .build());
        assertTrue(json.contains("\"mode\":\"inherit\""));
        assertTrue(json.contains("clientBearer"));
    }

    /**
     * 前提：mode 为不支持的值。
     * 期望：抛业务异常，提示 mode 非法。
     */
    @Test
    @Order(4)
    @DisplayName("非法 mode 拒绝")
    void toStorageJson_rejectsUnknownMode() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("cookie").build()));
        assertTrue(ex.getMessage().contains("auth.mode"));
    }

    /**
     * 前提：JSON 空白或非法。
     * 期望：parseOrInherit 回落 inherit。
     */
    @Test
    @Order(5)
    @DisplayName("空白 JSON 按 inherit")
    void parseOrInherit_blankDefaultsToInherit() {
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.parseOrInherit(null).getMode());
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.parseOrInherit("").getMode());
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.parseOrInherit("{").getMode());
    }
}
