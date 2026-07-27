package com.qualitest.flow.diagnose;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 告警 code 聚合逻辑单测：空值、去重保序、超长截断。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiFlowHealthPersistServiceTest
 */
class ApiFlowHealthPersistServiceTest {

    /**
     * 前提：aggregateCodes 入参为 null 或空列表。
     * 期望：返回 null。
     */
    @Test
    void aggregateCodes_nullOrEmpty_returnsNull() {
        assertNull(ApiFlowHealthPersistService.aggregateCodes(null));
        assertNull(ApiFlowHealthPersistService.aggregateCodes(List.of()));
    }

    /**
     * 前提：告警列表含重复 code（API_MISSING 出现两次）。
     * 期望：去重后按首次出现顺序拼接为 API_MISSING,ORPHAN_PARAM。
     */
    @Test
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
    void aggregateCodes_truncatesToMaxLen() {
        String longCode = "A".repeat(300);
        HttpNodeApiHealthWarning w = HttpNodeApiHealthWarning.of(
                longCode, "n1", "N1", 1L, "m", null);
        String codes = ApiFlowHealthPersistService.aggregateCodes(List.of(w));
        assertEquals(ApiFlowHealthPersistService.WARNING_CODES_MAX_LEN, codes.length());
    }
}
