package com.qualitest.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ApiImportUserConfigSupport：导入时用户覆盖层写入与「已配置」判定。
 * 边界：空 JSON/脚本；本地非空保留；双空写 "{}"；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiImportUserConfigSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiImportUserConfigSupportTest {

    /**
     * 前提：null、空串、{}、[]、null 字面量与含键 JSON。
     * 期望：前者视为未配置；{"a":1} 视为已配置。
     */
    @Test
    @Order(1)
    @DisplayName("用户 JSON：空值视为未配置，含键视为已配置")
    void isNonEmptyUserJson_emptyVsConfigured() {
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson(null));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson(""));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson("{}"));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson("[]"));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson("null"));
        assertTrue(ApiImportUserConfigSupport.isNonEmptyUserJson("{\"a\":1}"));
    }

    /**
     * 前提：null、空白与含内容的脚本字符串。
     * 期望：空白视为未配置；非空脚本视为已配置。
     */
    @Test
    @Order(2)
    @DisplayName("用户脚本：空白视为未配置，非空视为已配置")
    void isNonEmptyUserScript_blankVsConfigured() {
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserScript(null));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserScript("   "));
        assertTrue(ApiImportUserConfigSupport.isNonEmptyUserScript("console.log(1);"));
    }

    /**
     * 前提：本地 JSON 非空，上传包也有内容。
     * 期望：不调用 setter，保留本地值。
     */
    @Test
    @Order(3)
    @DisplayName("更新 JSON：本地非空时保留本地")
    void applyJsonFieldOnUpdate_localNonEmpty_preserves() {
        AtomicReference<String> written = new AtomicReference<>("unchanged");

        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(
                "{\"token\":\"local\"}",
                "{\"token\":\"incoming\"}",
                written::set);

        assertEquals("unchanged", written.get());
    }

    /**
     * 前提：本地 JSON 为 {}，上传包含 X-Trace 头。
     * 期望：写入上传包 JSON。
     */
    @Test
    @Order(4)
    @DisplayName("更新 JSON：本地为空时采用上传包")
    void applyJsonFieldOnUpdate_localEmpty_usesIncoming() {
        AtomicReference<String> written = new AtomicReference<>();

        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(
                "{}",
                "{\"X-Trace\":\"1\"}",
                written::set);

        assertEquals("{\"X-Trace\":\"1\"}", written.get());
    }

    /**
     * 前提：本地与上传包 JSON 均为空。
     * 期望：写入 "{}"。
     */
    @Test
    @Order(5)
    @DisplayName("更新 JSON：双方皆空时写入空对象")
    void applyJsonFieldOnUpdate_bothEmpty_setsEmptyObject() {
        AtomicReference<String> written = new AtomicReference<>();

        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(null, null, written::set);

        assertEquals("{}", written.get());
    }

    /**
     * 前提：本地脚本非空，上传包脚本不同。
     * 期望：不覆盖，保留本地脚本。
     */
    @Test
    @Order(6)
    @DisplayName("更新脚本：本地非空时保留本地")
    void applyScriptFieldOnUpdate_localNonEmpty_preserves() {
        AtomicReference<String> written = new AtomicReference<>("local-script");

        ApiImportUserConfigSupport.applyScriptFieldOnUpdate(
                "api.variables.set('a',1);",
                "api.variables.set('b',2);",
                written::set);

        assertEquals("local-script", written.get());
    }

    /**
     * 前提：本地与上传包脚本均为 null。
     * 期望：setter 收到 null。
     */
    @Test
    @Order(7)
    @DisplayName("更新脚本：本地为空时写入上传包（可为 null）")
    void applyScriptFieldOnUpdate_localEmpty_setsIncoming() {
        AtomicReference<String> written = new AtomicReference<>("placeholder");

        ApiImportUserConfigSupport.applyScriptFieldOnUpdate(null, null, written::set);

        assertNull(written.get());
    }
}
