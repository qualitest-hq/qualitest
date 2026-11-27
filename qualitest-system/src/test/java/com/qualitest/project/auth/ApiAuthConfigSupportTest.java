package com.qualitest.project.auth;

import com.qualitest.api.model.ApiAuthConfig;
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
 * 测谁：鉴权标签转落库 JSON 的校验与序列化、导入写入。
 * 覆盖：缺 mode 拒收、合法 none/inherit/override、非法 mode、override 缺 header、
 * 导入按上传 mode 落库（不会因预制列表改成 none）、缺 auth 拒收。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiAuthConfigSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiAuthConfigSupportTest {

    /**
     * 前提：auth 为 null 或 mode 空白。
     * 期望：抛业务异常。
     */
    @Test
    @Order(1)
    @DisplayName("缺 mode 拒收")
    void toStorageJson_rejectsMissingMode() {
        assertThrows(ServiceException.class, () -> ApiAuthConfigSupport.toStorageJson(null));
        assertThrows(ServiceException.class,
                () -> ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder().mode("  ").build()));
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
     * 前提：JSON 空白。
     * 期望：parse 抛错。
     */
    @Test
    @Order(6)
    @DisplayName("空白 JSON 拒收")
    void parse_blankRejects() {
        assertThrows(ServiceException.class, () -> ApiAuthConfigSupport.parse(null));
        assertThrows(ServiceException.class, () -> ApiAuthConfigSupport.parse(""));
        assertThrows(ServiceException.class, () -> ApiAuthConfigSupport.parse("{}"));
    }

    /**
     * 前提：非法 JSON。
     * 期望：抛业务异常。
     */
    @Test
    @Order(7)
    @DisplayName("非法 JSON 抛错")
    void parse_illegalThrows() {
        assertThrows(ServiceException.class, () -> ApiAuthConfigSupport.parse("{"));
    }

    /**
     * 前提：mode=override 且含 header。
     * 期望：JSON 含 mode 与 header，不含 authProfileId。
     */
    @Test
    @Order(8)
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
    @Order(9)
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
    @Order(10)
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
    @Order(11)
    @DisplayName("normalizeToJson 规范化 override")
    void normalizeToJson_override() {
        String stored = ApiAuthConfigSupport.normalizeToJson("""
                {"mode":"override","header":{"name":"Authorization","valueTemplate":"Bearer bad"}}
                """);
        ApiAuthConfig cfg = ApiAuthConfigSupport.parse(stored);
        assertEquals(ApiAuthConfig.MODE_OVERRIDE, cfg.getMode());
        assertEquals("Authorization", cfg.getHeader().getName());
        assertEquals("Bearer bad", cfg.getHeader().getValueTemplate());
    }

    /**
     * 前提：上传 mode=none。
     * 期望：落库瘦 JSON 仅含 mode=none。
     */
    @Test
    @Order(12)
    @DisplayName("导入：none 写出瘦 JSON")
    void applyOnImport_noneWritesLeanJson() {
        ApiAuthConfig incoming = ApiAuthConfig.builder().mode("none").build();
        String merged = ApiAuthConfigSupport.applyOnImport(incoming, null);
        assertTrue(merged.contains("\"none\""));
        assertFalse(merged.contains("inherit"));
        assertFalse(merged.contains("header"));
    }

    /**
     * 前提：上传 inherit 且未带 profileId。
     * 期望：回填 inheritProfileId，不改写成 none。
     */
    @Test
    @Order(13)
    @DisplayName("导入：inherit 回填 profileId")
    void applyOnImport_inheritFillsProfileId() {
        ApiAuthConfig incoming = ApiAuthConfig.builder().mode("inherit").build();
        String merged = ApiAuthConfigSupport.applyOnImport(incoming, "p1");
        assertTrue(merged.contains("\"inherit\""));
        assertTrue(merged.contains("p1"));
    }

    /**
     * 前提：上传未带 auth。
     * 期望：抛错。
     */
    @Test
    @Order(14)
    @DisplayName("导入：缺 auth 拒收")
    void applyOnImport_missingAuthRejects() {
        assertThrows(ServiceException.class,
                () -> ApiAuthConfigSupport.applyOnImport(null, "p1"));
    }
}
