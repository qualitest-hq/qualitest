package com.qualitest.flow.validate;

import com.qualitest.flow.run.GraphNodeAlwaysRunSupport;
import com.qualitest.flow.snapshot.GraphNodeSnapshotSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 GraphNodeBooleanFieldRules：alwaysRun / snapshotBefore 的类型与节点白名单校验，以及脏值收布尔。
 * 边界：纯函数，无 Spring / DB；覆盖合法收布尔、无法识别保留、非法节点类型报错。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphNodeBooleanFieldRulesTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphNodeBooleanFieldRulesTest {

    /**
     * 前提：HTTP 上 alwaysRun 为 "true" / 1 / "false" / 0。
     * 期望：收成真正布尔；无法识别的 "yes" 保持原样。
     */
    @Test
    @Order(1)
    @DisplayName("coerce 常见脏值为布尔")
    void coerceForNodeType_commonDirtyValues() {
        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN, "true");
        GraphNodeBooleanFieldRules.coerceForNodeType("http", data);
        assertEquals(Boolean.TRUE, data.get(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN));

        data.put(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN, 1);
        GraphNodeBooleanFieldRules.coerceForNodeType("http", data);
        assertEquals(Boolean.TRUE, data.get(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN));

        data.put(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE, "false");
        GraphNodeBooleanFieldRules.coerceForNodeType("http", data);
        assertEquals(Boolean.FALSE, data.get(GraphNodeSnapshotSupport.KEY_SNAPSHOT_BEFORE));

        data.put(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN, "yes");
        GraphNodeBooleanFieldRules.coerceForNodeType("http", data);
        assertEquals("yes", data.get(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN));
    }

    /**
     * 前提：assert 节点带 alwaysRun="true"。
     * 期望：不 coerce（非允许类型）；校验报不允许设置。
     */
    @Test
    @Order(2)
    @DisplayName("非法节点类型不 coerce 且校验报错")
    void disallowedType_noCoerce_validateError() {
        Map<String, Object> data = new HashMap<>();
        data.put(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN, "true");
        GraphNodeBooleanFieldRules.coerceForNodeType("assert", data);
        assertEquals("true", data.get(GraphNodeAlwaysRunSupport.KEY_ALWAYS_RUN));

        List<String> errors = new ArrayList<>();
        GraphNodeBooleanFieldRules.validate("nodes[0]", "assert", data, errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("不允许设置"));
    }

    /**
     * 前提：tryCoerceBoolean 边界输入。
     * 期望：true/false 族可识别；其它返回 null。
     */
    @Test
    @Order(3)
    @DisplayName("tryCoerceBoolean 边界")
    void tryCoerceBoolean_edges() {
        assertEquals(Boolean.TRUE, GraphNodeBooleanFieldRules.tryCoerceBoolean("TRUE"));
        assertEquals(Boolean.FALSE, GraphNodeBooleanFieldRules.tryCoerceBoolean(0));
        assertNull(GraphNodeBooleanFieldRules.tryCoerceBoolean("maybe"));
        assertNull(GraphNodeBooleanFieldRules.tryCoerceBoolean(2));
    }
}
