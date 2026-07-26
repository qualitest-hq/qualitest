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
 * {@link GraphMetaIoSupport} 单元测试：子流 inputs/outputs 默认映射与 flowOutput 名称提取。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=GraphMetaIoSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphMetaIoSupportTest {

    /**
     * meta.flowOutputs 应转为默认 outputs 映射（name 与 flowKey 同名）。
     * 期望：每条 flowOutput 对应一行 {name, flowKey}。
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
     * 当前激活场景的 flowSeed 键应建议为子流 inputs 占位符。
     * 期望：name=种子键名，value={{flow.键名}}。
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
     * flowOutputNames 应提取 meta.flowOutputs 中的 name 列表。
     * 期望：顺序与声明一致。
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
