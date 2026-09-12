package com.qualitest.ai.scenario.flow;

import com.qualitest.flow.delay.DelayConstants;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 Assign / Delay / Condition / Subflow / HTTP callMode 规范化。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignNodeNormalizersExtraTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignNodeNormalizersExtraTest {

    @Test
    @Order(1)
    @DisplayName("delay 缺 ms 默认 1000；超上限截断")
    void delayNormalize() {
        Map<String, Object> data = new HashMap<>();
        FlowDesignDelayNodeNormalizer.normalize(data);
        assertEquals(DelayConstants.DEFAULT_DELAY_MS, data.get("ms"));

        data.put("ms", 999_999);
        FlowDesignDelayNodeNormalizer.normalize(data);
        assertEquals(DelayConstants.MAX_DELAY_MS, data.get("ms"));
    }

    @Test
    @Order(2)
    @DisplayName("assign 空 op → set；scope 固定 flow")
    void assignNormalize() {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("name", " x ");
        row.put("op", "");
        row.put("value", "1");
        rows.add(row);
        data.put("assignments", rows);
        FlowDesignAssignNodeNormalizer.normalize(data);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> out = (List<Map<String, Object>>) data.get("assignments");
        assertEquals("flow", out.get(0).get("scope"));
        assertEquals("x", out.get(0).get("name"));
        assertEquals("set", out.get(0).get("op"));
    }

    @Test
    @Order(3)
    @DisplayName("condition 缺 branches 补 IF/ELSE")
    void conditionDefaultBranches() {
        Map<String, Object> data = new HashMap<>();
        FlowDesignConditionNodeNormalizer.normalize(data);
        Object branches = data.get("branches");
        assertTrue(branches instanceof List<?> list && list.size() == 2);
    }

    @Test
    @Order(6)
    @DisplayName("condition stripBranchTargets 剔除预写 target")
    void conditionStripTargets() {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> branches = new ArrayList<>();
        Map<String, Object> ifBranch = new HashMap<>();
        ifBranch.put("id", "1");
        ifBranch.put("kind", "if");
        ifBranch.put("target", "should-go");
        ifBranch.put("conditions", List.of());
        branches.add(ifBranch);
        data.put("branches", branches);
        FlowDesignConditionNodeNormalizer.stripBranchTargets(data);
        assertFalse(ifBranch.containsKey("target"));
    }

    @Test
    @Order(4)
    @DisplayName("subflow 缺 versionPolicy 默认 latest")
    void subflowVersionPolicy() {
        Map<String, Object> data = new HashMap<>();
        FlowDesignSubflowNodeNormalizer.normalize(data);
        assertEquals("latest", data.get("versionPolicy"));
    }

    @Test
    @Order(5)
    @DisplayName("http 缺 callMode 默认 project")
    void httpCallModeDefault() {
        Map<String, Object> data = new HashMap<>();
        FlowDesignHttpNodeNormalizer.normalizeWithoutApi(data);
        assertEquals("project", data.get("callMode"));
    }
}
