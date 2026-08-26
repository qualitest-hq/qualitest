package com.qualitest.api.util;

import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：鉴权标签转落库 JSON 的校验与序列化、更新导入合并。
 * 边界：未声明 auth、合法 none/inherit/override、非法 mode、缺 header；更新时 hint 本地优先。
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
     * 前提：mode 大小写混用。
     * 期望：规范为小写后再落库。
     */
    @Test
    @Order(4)
    @DisplayName("mode 规范为小写")
    void toStorageJson_canonicalizesModeCase() {
        String json = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("NONE").build());
        assertTrue(json.contains("\"mode\":\"none\""));
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.canonicalizeMode("Inherit"));
    }

    /**
     * 前提：mode 为不支持的值。
     * 期望：抛业务异常，提示 mode 非法。
     */
    @Test
    @Order(5)
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
    @Order(6)
    @DisplayName("空白 JSON 按 inherit")
    void parseOrInherit_blankDefaultsToInherit() {
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.parseOrInherit(null).getMode());
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.parseOrInherit("").getMode());
        assertEquals(ApiAuthConfig.MODE_INHERIT, ApiAuthConfigSupport.parseOrInherit("{").getMode());
    }

    /**
     * 前提：mode=override 且含 header。
     * 期望：JSON 含 mode 与 header，不含 authProfileId。
     */
    @Test
    @Order(7)
    @DisplayName("override 落库含 header")
    void toStorageJson_overrideWithHeader() {
        String json = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder()
                .mode("override")
                .authProfileId("shouldDrop")
                .header(ApiAuthConfig.Header.builder()
                        .name("Authorization")
                        .valueTemplate("Bearer {{flow.adminToken}}")
                        .build())
                .build());
        assertTrue(json.contains("\"mode\":\"override\""));
        assertTrue(json.contains("Authorization"));
        assertTrue(json.contains("adminToken"));
        assertFalse(json.contains("shouldDrop"));
        assertFalse(json.contains("authProfileId"));
    }

    /**
     * 前提：mode=override 缺 header。
     * 期望：ServiceException。
     */
    @Test
    @Order(8)
    @DisplayName("override 缺 header 拒绝")
    void toStorageJson_overrideRequiresHeader() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("override").build()));
        assertTrue(ex.getMessage().contains("header"));
    }

    /**
     * 前提：inherit 误带 header。
     * 期望：落库不含 header 字段。
     */
    @Test
    @Order(9)
    @DisplayName("inherit 不残留 header")
    void toStorageJson_inheritDropsHeader() {
        String json = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder()
                .mode("inherit")
                .header(ApiAuthConfig.Header.builder()
                        .name("Authorization")
                        .valueTemplate("Bearer x")
                        .build())
                .build());
        assertTrue(json.contains("\"mode\":\"inherit\""));
        assertFalse(json.contains("header"));
        assertFalse(json.contains("Bearer"));
    }

    /**
     * 前提：合法 override JSON 字符串。
     * 期望：normalizeToJson 产出可解析的 override。
     */
    @Test
    @Order(10)
    @DisplayName("normalizeToJson 规范化 override")
    void normalizeToJson_override() {
        String stored = ApiAuthConfigSupport.normalizeToJson("""
                {"mode":"override","header":{"name":"Authorization","valueTemplate":"Bearer bad"}}
                """);
        ApiAuthConfig cfg = ApiAuthConfigSupport.parseOrInherit(stored);
        assertEquals(ApiAuthConfig.MODE_OVERRIDE, cfg.getMode());
        assertEquals("Authorization", cfg.getHeader().getName());
        assertEquals("Bearer bad", cfg.getHeader().getValueTemplate());
    }

    /**
     * 前提：本地登录口曾带 loginHint；上传包 mode=none。
     * 期望：落库仅 mode=none，不保留 loginHint。
     */
    @Test
    @Order(11)
    @DisplayName("更新导入：上传 none 写出瘦 JSON，无 loginHint")
    void mergeOnImportUpdate_noneWritesLeanJson() {
        String local = "{\"mode\":\"none\",\"loginHint\":{\"flowKey\":\"token\",\"from\":\"body\",\"expr\":\"$.token\"}}";
        ApiAuthConfig incoming = ApiAuthConfig.builder().mode("none").build();

        String merged = ApiAuthConfigSupport.mergeOnImportUpdate(local, incoming, true, null);

        assertTrue(merged.contains("\"none\""));
        assertFalse(merged.contains("loginHint"));
        assertFalse(merged.contains("$.token"));
    }

    /**
     * 前提：本地免登口曾有 hint；上传 inherit。
     * 期望：强制 none，且不写 loginHint。
     */
    @Test
    @Order(12)
    @DisplayName("更新导入：上传 inherit 对免登口强制 none，无 loginHint")
    void mergeOnImportUpdate_inheritAnonymousForcesNoneNoHint() {
        String local = "{\"mode\":\"none\",\"loginHint\":{\"flowKey\":\"adminToken\",\"from\":\"body\",\"expr\":\"$.token\"}}";
        ApiAuthConfig incoming = ApiAuthConfig.builder().mode("inherit").build();

        String merged = ApiAuthConfigSupport.mergeOnImportUpdate(local, incoming, true, "p1");

        assertTrue(merged.contains("\"none\""));
        assertFalse(merged.contains("loginHint"));
        assertFalse(merged.contains("adminToken"));
        assertFalse(merged.contains("inherit"));
    }

    /**
     * 前提：非免登口；上传未带 auth。
     * 期望：返回 null，表示不改库中已有值。
     */
    @Test
    @Order(13)
    @DisplayName("更新导入：未带 auth 且非免登则不改")
    void mergeOnImportUpdate_noAuthNonAnonymous_skips() {
        assertNull(ApiAuthConfigSupport.mergeOnImportUpdate(
                "{\"mode\":\"inherit\"}", null, false, "p1"));
    }

    /**
     * 前提：免登口本地曾有 hint；上传未带 auth。
     * 期望：补 none，不保留 loginHint。
     */
    @Test
    @Order(14)
    @DisplayName("更新导入：未带 auth 的免登口补 none，无 loginHint")
    void mergeOnImportUpdate_noAuthAnonymous_writesNoneNoHint() {
        String local = "{\"mode\":\"none\",\"loginHint\":{\"flowKey\":\"token\",\"from\":\"body\",\"expr\":\"$.token\"}}";

        String merged = ApiAuthConfigSupport.mergeOnImportUpdate(local, null, true, null);

        assertTrue(merged.contains("\"none\""));
        assertFalse(merged.contains("loginHint"));
    }
}
