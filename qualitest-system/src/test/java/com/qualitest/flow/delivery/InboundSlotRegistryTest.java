package com.qualitest.flow.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 开放 HTTP 入站名额：占满拒绝、释放后再占、重复释放不把计数打成负。
 * 边界：maxSlots=1；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=InboundSlotRegistryTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InboundSlotRegistryTest {

    /**
     * 前提：上限 1。
     * 期望：第一次 acquire 成功；第二次 null；release 后再 acquire 成功。
     */
    @Test
    @Order(1)
    @DisplayName("占满后拒绝，释放后再占")
    void acquire_fullThenRelease() {
        InboundSlotRegistry reg = new InboundSlotRegistry(1);
        String a = reg.tryAcquire();
        assertNotNull(a);
        assertNull(reg.tryAcquire());
        assertEquals(1, reg.heldCount());
        reg.release(a);
        assertEquals(0, reg.heldCount());
        assertNotNull(reg.tryAcquire());
    }

    /**
     * 前提：已释放一次。
     * 期望：再次 release 同 token 不把计数打成负。
     */
    @Test
    @Order(2)
    @DisplayName("重复释放无副作用")
    void release_idempotent() {
        InboundSlotRegistry reg = new InboundSlotRegistry(2);
        String a = reg.tryAcquire();
        reg.release(a);
        reg.release(a);
        assertEquals(0, reg.heldCount());
    }
}
