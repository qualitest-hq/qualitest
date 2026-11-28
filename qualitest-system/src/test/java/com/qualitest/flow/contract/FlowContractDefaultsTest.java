package com.qualitest.flow.contract;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphSchemaVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowContractDefaults：跨类型落库补写与 NO_RETURN 体。
 * 边界：显式旧 schemaVersion 不覆盖；HTTP/子流专属默认见各自测试。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowContractDefaultsTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowContractDefaultsTest {

    /**
     * 前提：返回缺 from、入口缺 maxSteps、meta 无 schemaVersion。
     * 期望：三者均被补齐。
     */
    @Test
    @Order(1)
    @DisplayName("落库补 from / maxSteps / schemaVersion")
    void applySaveDefaults_stampsSchemaVersionAndFromAndMaxSteps() {
        GraphNode ret = new GraphNode();
        ret.setId("r1");
        ret.setType("http-return");
        ret.setData(new LinkedHashMap<>());

        GraphNode entry = new GraphNode();
        entry.setId("e1");
        entry.setType("http-entry");
        entry.setData(new LinkedHashMap<>());

        GraphJson graph = new GraphJson();
        graph.setNodes(new ArrayList<>(List.of(entry, ret)));
        graph.setMeta(new GraphMeta());

        assertTrue(FlowContractDefaults.applySaveDefaults(graph));
        assertEquals(GraphSchemaVersions.CURRENT, graph.getMeta().getSchemaVersion());
        assertEquals(FlowContractDefaults.DEFAULT_FROM, ret.getData().get("from"));
        assertEquals(FlowContractDefaults.DEFAULT_MAX_STEPS, entry.getData().get("maxSteps"));
    }

    /**
     * 前提：meta.schemaVersion 已为 1。
     * 期望：不覆盖、无改动。
     */
    @Test
    @Order(2)
    @DisplayName("不覆盖显式旧 schemaVersion")
    void applySaveDefaults_doesNotOverwriteExplicitOldSchemaVersion() {
        GraphJson graph = new GraphJson();
        GraphMeta meta = new GraphMeta();
        meta.setSchemaVersion(1);
        graph.setMeta(meta);
        graph.setNodes(List.of());

        assertFalse(FlowContractDefaults.applySaveDefaults(graph));
        assertEquals(1, graph.getMeta().getSchemaVersion());
    }

    /**
     * 前提：调用 defaultNoReturnBody。
     * 期望：code/message 为契约常量。
     */
    @Test
    @Order(3)
    @DisplayName("NO_RETURN 业务对象形状")
    void defaultNoReturnBody_shape() {
        Map<String, Object> body = FlowContractDefaults.defaultNoReturnBody();
        assertEquals(FlowContractDefaults.NO_RETURN_CODE, body.get("code"));
        assertEquals(FlowContractDefaults.NO_RETURN_MESSAGE, body.get("message"));
    }
}
