package com.qualitest.flow.snapshot;

import com.qualitest.project.domain.TestProjectEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SnapshotEnvSupport} 单元测试：环境还原开关与 test-support 根路径解析。
 * <p>
 * 覆盖 allowDestructiveReset=1/0 时的判定，以及 envUrl 派生 /test-support 地址。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=SnapshotEnvSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotEnvSupportTest {

    /**
     * allowDestructiveReset=1 且 envUrl 有效。
     * 期望：isResetAllowed=true；resolveResetBase 为 envUrl + /test-support。
     */
    @Test
    @Order(1)
    void isResetAllowed_whenFlagOn() {
        begin("isResetAllowed_whenFlagOn");
        TestProjectEnv env = TestProjectEnv.builder()
                .envUrl("http://localhost:8081")
                .allowDestructiveReset(1)
                .build();
        assertTrue(SnapshotEnvSupport.isResetAllowed(env));
        String base = SnapshotEnvSupport.resolveResetBase(env);
        assertEquals("http://localhost:8081/test-support", base);
        log("resetAllowed=true resetBase=" + base);
        end("isResetAllowed_whenFlagOn");
    }

    /**
     * allowDestructiveReset=0。
     * 期望：isResetAllowed=false（checkpoint/restore 将静默跳过）。
     */
    @Test
    @Order(2)
    void isResetAllowed_whenFlagOff() {
        begin("isResetAllowed_whenFlagOff");
        TestProjectEnv env = TestProjectEnv.builder().allowDestructiveReset(0).build();
        assertFalse(SnapshotEnvSupport.isResetAllowed(env));
        log("resetAllowed=false");
        end("isResetAllowed_whenFlagOff");
    }
}
