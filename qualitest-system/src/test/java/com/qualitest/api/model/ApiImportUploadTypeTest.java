package com.qualitest.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiImportUploadType：code 解析与种子策略。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiImportUploadTypeTest
 */
class ApiImportUploadTypeTest {

    @Test
    @DisplayName("仅 project 触发鉴权种子")
    void onlyProjectSeedsAuth() {
        assertTrue(ApiImportUploadType.PROJECT.seedProjectAuthIfEmpty());
        assertFalse(ApiImportUploadType.CONTROLLER_ALL.seedProjectAuthIfEmpty());
        assertFalse(ApiImportUploadType.CONTROLLER_SELECT.seedProjectAuthIfEmpty());
    }

    @Test
    @DisplayName("fromCode 认 JSON code 与枚举名")
    void fromCode_parsesCodes() {
        assertEquals(ApiImportUploadType.PROJECT, ApiImportUploadType.fromCode("project"));
        assertEquals(ApiImportUploadType.CONTROLLER_ALL, ApiImportUploadType.fromCode("controllerAll"));
        assertEquals(ApiImportUploadType.CONTROLLER_SELECT, ApiImportUploadType.fromCode("controllerSelect"));
        assertEquals(ApiImportUploadType.PROJECT, ApiImportUploadType.fromCode("PROJECT"));
        assertNull(ApiImportUploadType.fromCode(null));
        assertNull(ApiImportUploadType.fromCode("unknown"));
    }
}
