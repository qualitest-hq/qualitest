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
 * 测图元数据子流输入输出辅助。
 * 边界：默认输出映射、建议输入映射、产出名称与 JSON、入参解析规范化；无数据库。
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

    /**
     * 前提：meta.flowOutputs 含 token 与 description。
     * 期望：flowOutputsJson 保留 name/description。
     */
    @Test
    @Order(4)
    @DisplayName("提取：flowOutputs JSON 含 description")
    void flowOutputsJson_includesDescription() {
        GraphMeta meta = GraphMeta.builder()
                .flowOutputs(List.of(GraphFlowOutput.builder()
                        .name("token")
                        .description("访问令牌")
                        .build()))
                .build();

        var arr = GraphMetaIoSupport.flowOutputsJson(meta);
        assertEquals(1, arr.size());
        assertEquals("token", arr.getJSONObject(0).getString("name"));
        assertEquals("访问令牌", arr.getJSONObject(0).getString("description"));
    }

    /**
     * 前提：入参为含 name/description 的对象列表，夹杂空 name。
     * 期望：parseFlowOutputsArg 规范化后仅保留有效项。
     */
    @Test
    @Order(5)
    @DisplayName("解析：flowOutputs 入参规范化")
    void parseFlowOutputsArg_normalizesRows() {
        List<GraphFlowOutput> parsed = GraphMetaIoSupport.parseFlowOutputsArg(List.of(
                Map.of("name", " token ", "description", " 访问令牌 "),
                Map.of("name", "  "),
                Map.of("name", "refreshToken")
        ));
        assertEquals(2, parsed.size());
        assertEquals("token", parsed.get(0).getName());
        assertEquals("访问令牌", parsed.get(0).getDescription());
        assertEquals("refreshToken", parsed.get(1).getName());
    }
}
