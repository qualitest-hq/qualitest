package com.qualitest.flow.diagnose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测 ApiFlowHealthPersistService.aggregateCodes：告警 code 聚合。
 * 边界：纯函数；空值、去重保序、超长截断。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiFlowHealthPersistServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiFlowHealthPersistServiceTest {

    /**
     * 前提：aggregateCodes 入参为 null 或空列表。
     * 期望：返回 null。
     */
    @Test
    @Order(1)
    @DisplayName("空入参 aggregateCodes 返回 null")
    void aggregateCodes_nullOrEmpty_returnsNull() {
        assertNull(ApiFlowHealthPersistService.aggregateCodes(null));
        assertNull(ApiFlowHealthPersistService.aggregateCodes(List.of()));
    }

    /**
     * 前提：告警列表含重复 code（API_MISSING 出现两次）。
     * 期望：去重后按首次出现顺序拼接为 API_MISSING,ORPHAN_PARAM。
     */
    @Test
    @Order(2)
    @DisplayName("告警 code 去重保序拼接")
    void aggregateCodes_dedupesInOrder() {
        HttpNodeApiHealthWarning a = HttpNodeApiHealthWarning.of(
                "API_MISSING", "n1", "N1", 1L, "m1", null);
        HttpNodeApiHealthWarning b = HttpNodeApiHealthWarning.of(
                "ORPHAN_PARAM", "n2", "N2", 1L, "m2", "x");
        HttpNodeApiHealthWarning a2 = HttpNodeApiHealthWarning.of(
                "API_MISSING", "n3", "N3", 2L, "m3", null);
        assertEquals("API_MISSING,ORPHAN_PARAM",
                ApiFlowHealthPersistService.aggregateCodes(List.of(a, b, a2)));
    }

    /**
     * 前提：单条告警 code 长度超过 WARNING_CODES_MAX_LEN。
     * 期望：拼接结果被截断至 WARNING_CODES_MAX_LEN。
     */
    @Test
    @Order(3)
    @DisplayName("超长 code 截断至上限")
    void aggregateCodes_truncatesToMaxLen() {
        String longCode = "A".repeat(300);
        HttpNodeApiHealthWarning w = HttpNodeApiHealthWarning.of(
                longCode, "n1", "N1", 1L, "m", null);
        String codes = ApiFlowHealthPersistService.aggregateCodes(List.of(w));
        assertEquals(ApiFlowHealthPersistService.WARNING_CODES_MAX_LEN, codes.length());
    }
}
