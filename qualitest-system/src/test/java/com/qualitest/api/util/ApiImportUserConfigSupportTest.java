package com.qualitest.api.util;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.concurrent.atomic.AtomicReference;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入覆盖层写入规则单元测试。
 * <p>
 * 覆盖：JSON/脚本是否已配置的判定，以及本地非空保留、本地空写上传包、双空写 "{}"。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ApiImportUserConfigSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiImportUserConfigSupportTest {

    /** 空对象、空数组、null 字面量应视为未配置；含键值的 JSON 视为已配置。 */
    @Test
    @Order(1)
    void isNonEmptyUserJson() {
        begin("isNonEmptyUserJson");
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson(null));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson(""));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson("{}"));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson("[]"));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserJson("null"));
        assertTrue(ApiImportUserConfigSupport.isNonEmptyUserJson("{\"a\":1}"));
        log("empty/null rejected; object with keys accepted");
        end("isNonEmptyUserJson");
    }

    /** 脚本 trim 后非空视为已配置。 */
    @Test
    @Order(2)
    void isNonEmptyUserScript() {
        begin("isNonEmptyUserScript");
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserScript(null));
        assertFalse(ApiImportUserConfigSupport.isNonEmptyUserScript("   "));
        assertTrue(ApiImportUserConfigSupport.isNonEmptyUserScript("console.log(1);"));
        log("blank script=false; content=true");
        end("isNonEmptyUserScript");
    }

    /** 本地 JSON 非空时不调用 setter。 */
    @Test
    @Order(3)
    void applyJsonFieldOnUpdate_localNonEmpty_preserves() {
        begin("applyJsonFieldOnUpdate_localNonEmpty_preserves");
        AtomicReference<String> written = new AtomicReference<>("unchanged");

        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(
                "{\"token\":\"local\"}",
                "{\"token\":\"incoming\"}",
                written::set);

        assertEquals("unchanged", written.get());
        log("local headers preserved");
        end("applyJsonFieldOnUpdate_localNonEmpty_preserves");
    }

    /** 本地 JSON 为空且上传包有内容时，应写入上传包内容。 */
    @Test
    @Order(4)
    void applyJsonFieldOnUpdate_localEmpty_usesIncoming() {
        begin("applyJsonFieldOnUpdate_localEmpty_usesIncoming");
        AtomicReference<String> written = new AtomicReference<>();

        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(
                "{}",
                "{\"X-Trace\":\"1\"}",
                written::set);

        assertEquals("{\"X-Trace\":\"1\"}", written.get());
        log("incoming cookies applied");
        end("applyJsonFieldOnUpdate_localEmpty_usesIncoming");
    }

    /** 本地与上传包均为空时，应写入空对象 JSON。 */
    @Test
    @Order(5)
    void applyJsonFieldOnUpdate_bothEmpty_setsEmptyObject() {
        begin("applyJsonFieldOnUpdate_bothEmpty_setsEmptyObject");
        AtomicReference<String> written = new AtomicReference<>();

        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(null, null, written::set);

        assertEquals("{}", written.get());
        log("both empty -> {}");
        end("applyJsonFieldOnUpdate_bothEmpty_setsEmptyObject");
    }

    /** 本地脚本非空时不覆盖。 */
    @Test
    @Order(6)
    void applyScriptFieldOnUpdate_localNonEmpty_preserves() {
        begin("applyScriptFieldOnUpdate_localNonEmpty_preserves");
        AtomicReference<String> written = new AtomicReference<>("local-script");

        ApiImportUserConfigSupport.applyScriptFieldOnUpdate(
                "api.variables.set('a',1);",
                "api.variables.set('b',2);",
                written::set);

        assertEquals("local-script", written.get());
        log("local pre script preserved");
        end("applyScriptFieldOnUpdate_localNonEmpty_preserves");
    }

    /** 本地脚本为空时写入上传包脚本（可为 null）。 */
    @Test
    @Order(7)
    void applyScriptFieldOnUpdate_localEmpty_setsIncoming() {
        begin("applyScriptFieldOnUpdate_localEmpty_setsIncoming");
        AtomicReference<String> written = new AtomicReference<>("placeholder");

        ApiImportUserConfigSupport.applyScriptFieldOnUpdate(null, null, written::set);

        assertNull(written.get());
        log("empty local -> incoming null");
        end("applyScriptFieldOnUpdate_localEmpty_setsIncoming");
    }
}
