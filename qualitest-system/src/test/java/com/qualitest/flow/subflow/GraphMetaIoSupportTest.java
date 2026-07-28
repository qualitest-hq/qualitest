package com.qualitest.flow.subflow;

import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphMetaIoSupport：子流 inputs/outputs 默认映射与 flowOutput 名称提取。
 * 边界：activeScenario flowSeed；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphMetaIoSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphMetaIoSupportTest {

    /**
     * 前提：meta.flowOutputs 含 token、refreshToken。
     * 期望：默认 outputs 两行，name 与 flowKey 同名。
     */
    @Test
    @Order(1)
    @DisplayName("默认 outputs：来自 meta.flowOutputs")
    void defaultOutputMappings_fromFlowOutputs() {
        GraphMeta meta = GraphMeta.builder()
                .flowOutputs(List.of(
                        GraphFlowOutput.builder().name("token").description("访问令牌").build(),
                        GraphFlowOutput.builder().name("refreshToken").build()
                ))
                .build();

        List<Map<String, String>> mappings = GraphMetaIoSupport.defaultOutputMappings(meta);

        assertEquals(2, mappings.size());
        assertEquals("token", mappings.get(0).get("name"));
        assertEquals("token", mappings.get(0).get("flowKey"));
        assertEquals("refreshToken", mappings.get(1).get("name"));
        assertEquals("refreshToken", mappings.get(1).get("flowKey"));
    }

    /**
     * 前提：activeScenario 的 flowSeed 含 loginUser。
     * 期望：inputs 一行 name=loginUser、value={{flow.loginUser}}。
     */
    @Test
    @Order(2)
    @DisplayName("建议 inputs：来自 activeScenario.flowSeed")
    void suggestInputMappings_fromActiveScenarioFlowSeed() {
        GraphRunScenario scenario = GraphRunScenario.builder()
                .id("s1")
                .name("默认")
                .testProjectEnvId("")
                .flowSeed(new HashMap<>(Map.of("loginUser", "admin")))
                .build();
        GraphMeta meta = GraphMeta.builder()
                .activeScenarioId("s1")
                .scenarios(List.of(scenario))
                .build();

        List<Map<String, String>> inputs = GraphMetaIoSupport.suggestInputMappings(meta);

        assertEquals(1, inputs.size());
        assertEquals("loginUser", inputs.get(0).get("name"));
        assertEquals("{{flow.loginUser}}", inputs.get(0).get("value"));
    }

    /**
     * 前提：meta.flowOutputs 仅 token。
     * 期望：flowOutputNames=[token]。
     */
    @Test
    @Order(3)
    @DisplayName("提取：flowOutput 名称列表")
    void flowOutputNames_extractsNames() {
        GraphMeta meta = GraphMeta.builder()
                .flowOutputs(List.of(GraphFlowOutput.builder().name("token").build()))
                .build();

        assertEquals(List.of("token"), GraphMetaIoSupport.flowOutputNames(meta));
    }
}
