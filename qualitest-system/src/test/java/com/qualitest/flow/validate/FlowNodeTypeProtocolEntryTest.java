package com.qualitest.flow.validate;

import com.qualitest.flow.model.FlowNodeType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：FlowNodeType 协议入口识别。
 * 边界：tcp-entry 为已知入口；平台三入口不算协议入口；非法后缀拒绝。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowNodeTypeProtocolEntryTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowNodeTypeProtocolEntryTest {

    /**
     * 前提：type=tcp-entry。
     * 期望：isKnown / isEntry / isProtocolEntry 为真，短名 tcp。
     */
    @Test
    @Order(1)
    @DisplayName("协议入口 tcp-entry")
    void tcpEntryRecognized() {
        assertTrue(FlowNodeType.isKnown("tcp-entry"));
        assertTrue(FlowNodeType.isEntry("tcp-entry"));
        assertTrue(FlowNodeType.isProtocolEntry("tcp-entry"));
        assertEquals("tcp", FlowNodeType.protocolShortName("tcp-entry"));
    }

    /**
     * 前提：三种平台入口。
     * 期望：是入口但非协议入口。
     */
    @Test
    @Order(2)
    @DisplayName("平台入口不算协议入口")
    void platformEntriesNotProtocol() {
        assertTrue(FlowNodeType.isEntry("http-entry"));
        assertFalse(FlowNodeType.isProtocolEntry("http-entry"));
        assertFalse(FlowNodeType.isProtocolEntry("scenario-entry"));
        assertFalse(FlowNodeType.isProtocolEntry("subflow-entry"));
    }

    /**
     * 前提：pluginAction。
     * 期望：已知非入口。
     */
    @Test
    @Order(3)
    @DisplayName("pluginAction 已知非入口")
    void pluginActionKnown() {
        assertTrue(FlowNodeType.isKnown("pluginAction"));
        assertFalse(FlowNodeType.isEntry("pluginAction"));
    }
}
