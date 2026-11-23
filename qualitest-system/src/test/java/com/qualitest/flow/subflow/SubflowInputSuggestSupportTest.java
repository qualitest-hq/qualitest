package com.qualitest.flow.subflow;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 SubflowInputSuggestSupport：按主入口首场景 inSeed 建议子流 inputs。
 * 边界：无场景、空 inSeed。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubflowInputSuggestSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowInputSuggestSupportTest {

  @Test
  @Order(1)
  @DisplayName("建议 inputs：来自首条场景 inSeed")
  void suggestInputMappings_fromInSeed() {
    Map<String, Object> data = new HashMap<>();
    data.put(
        "scenarios",
        List.of(
            GraphRunScenario.builder()
                .id("sc-1")
                .name("默认")
                .inSeed(Map.of("orderId", "o-1"))
                .build()));

    GraphJson graph =
        GraphJson.builder()
            .meta(GraphMeta.builder().build())
            .nodes(
                List.of(
                    GraphNode.builder()
                        .id("entry")
                        .type("scenario-entry")
                        .name("入口")
                        .position(com.qualitest.flow.model.GraphNodePosition.builder().x(0).y(0).build())
                        .data(data)
                        .build()))
            .build();

    List<Map<String, String>> mappings = SubflowInputSuggestSupport.suggestInputMappings(graph);
    assertEquals(1, mappings.size());
    assertEquals("orderId", mappings.get(0).get("name"));
    assertEquals("{{flow.orderId}}", mappings.get(0).get("value"));
  }

  @Test
  @Order(2)
  @DisplayName("建议 inputs：无场景时为空")
  void suggestInputMappings_emptyWhenNoScenario() {
    GraphJson graph = GraphJson.builder().meta(GraphMeta.builder().build()).nodes(List.of()).build();
    assertTrue(SubflowInputSuggestSupport.suggestInputMappings(graph).isEmpty());
  }
}