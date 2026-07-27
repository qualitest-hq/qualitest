package com.qualitest.flow.snapshot;

import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 SnapshotEnvSupport：破坏性重置开关与 /test-support 根路径。
 * 边界：allowDestructiveReset=1/0。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SnapshotEnvSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotEnvSupportTest {

    /**
     * 前提：allowDestructiveReset=1，envUrl=http://localhost:8081。
     * 期望：isResetAllowed=true；resolveResetBase=envUrl+/test-support。
     */
    @Test
    @Order(1)
    void isResetAllowed_flagEnabled() {
        TestProjectEnv env = TestProjectEnv.builder()
                .envUrl("http://localhost:8081")
                .allowDestructiveReset(1)
                .build();
        assertTrue(SnapshotEnvSupport.isResetAllowed(env), "allowDestructiveReset=1 时应允许重置");
        String base = SnapshotEnvSupport.resolveResetBase(env);
        assertEquals("http://localhost:8081/test-support", base, "重置根地址应为 envUrl + /test-support");
    }

    /**
     * 前提：allowDestructiveReset=0。
     * 期望：isResetAllowed=false。
     */
    @Test
    @Order(2)
    void isResetAllowed_flagDisabled() {
        TestProjectEnv env = TestProjectEnv.builder().allowDestructiveReset(0).build();
        assertFalse(SnapshotEnvSupport.isResetAllowed(env), "allowDestructiveReset=0 时应禁止重置");
    }
}
