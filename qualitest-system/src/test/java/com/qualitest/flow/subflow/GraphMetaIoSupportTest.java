package com.qualitest.flow.subflow;

import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphMetaIoSupport：子流 inputs/outputs 默认映射与 flowOutput 名称提取。
 * 边界：activeScenario flowSeed；存量 begin/end 保留。
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
    void defaultOutputMappings_fromFlowOutputs() {
        begin("defaultOutputMappings_fromFlowOutputs");
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
        log("mappings=" + mappings.size() + " first=" + quote(mappings.get(0).get("flowKey")));
        end("defaultOutputMappings_fromFlowOutputs");
    }

    /**
     * 前提：activeScenario 的 flowSeed 含 loginUser。
     * 期望：inputs 一行 name=loginUser、value={{flow.loginUser}}。
     */
    @Test
    @Order(2)
    void suggestInputMappings_fromActiveScenarioFlowSeed() {
        begin("suggestInputMappings_fromActiveScenarioFlowSeed");
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
        log("input=" + quote(inputs.get(0).get("value")));
        end("suggestInputMappings_fromActiveScenarioFlowSeed");
    }

    /**
     * 前提：meta.flowOutputs 仅 token。
     * 期望：flowOutputNames=[token]。
     */
    @Test
    @Order(3)
    void flowOutputNames_extractsNames() {
        begin("flowOutputNames_extractsNames");
        GraphMeta meta = GraphMeta.builder()
                .flowOutputs(List.of(GraphFlowOutput.builder().name("token").build()))
                .build();

        assertEquals(List.of("token"), GraphMetaIoSupport.flowOutputNames(meta));
        log("names=" + GraphMetaIoSupport.flowOutputNames(meta));
        end("flowOutputNames_extractsNames");
    }
}
