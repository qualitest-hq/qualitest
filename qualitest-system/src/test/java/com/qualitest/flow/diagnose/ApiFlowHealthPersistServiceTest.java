package com.qualitest.flow.diagnose;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 告警 code 聚合逻辑单测：空值、去重保序、超长截断。
 */
class ApiFlowHealthPersistServiceTest {

    /** 空入参应得到 null */
    @Test
    void aggregateCodes_nullOrEmpty_returnsNull() {
        assertNull(ApiFlowHealthPersistService.aggregateCodes(null));
        assertNull(ApiFlowHealthPersistService.aggregateCodes(List.of()));
    }

    /** 相同 code 只保留首次出现，顺序为首次出现顺序 */
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

    /** 拼接结果超过字段上限时截断到 WARNING_CODES_MAX_LEN */
    @Test
    void aggregateCodes_truncatesToMaxLen() {
        String longCode = "A".repeat(300);
        HttpNodeApiHealthWarning w = HttpNodeApiHealthWarning.of(
                longCode, "n1", "N1", 1L, "m", null);
        String codes = ApiFlowHealthPersistService.aggregateCodes(List.of(w));
        assertEquals(ApiFlowHealthPersistService.WARNING_CODES_MAX_LEN, codes.length());
    }
}
