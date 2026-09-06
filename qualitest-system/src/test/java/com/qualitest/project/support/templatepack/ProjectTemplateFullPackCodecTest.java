package com.qualitest.project.support.templatepack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.support.templatepack.ProjectTemplateFullPackCodec.PackKind;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ExpandResult;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完整包编解码单测。
 * 覆盖：往返保留 flows、空 flows、精简包仍可展开、formatVersion 过大拒绝、缺省版本可导入。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectTemplateFullPackCodecTest {

    private final ProjectTemplateFullPackCodec codec = new ProjectTemplateFullPackCodec();
    private final ProjectTemplateSlimExpander expander = new ProjectTemplateSlimExpander();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @Order(1)
    @DisplayName("完整包 round-trip 保留 flows")
    void fullPack_roundTrip_keepsFlows() throws Exception {
        // 前提：实体含非空 templateFlows
        TestProjectTemplate entity = TestProjectTemplate.builder()
                .templateName("Pack Demo")
                .matchConfig("{\"pathPrefix\":[\"/api/\"]}")
                .templateApis("[{\"apiName\":\"登录\",\"apiPath\":\"/login\"}]")
                .templateParams("[{\"kind\":\"asset\",\"name\":\"adminAuth\",\"value\":{\"username\":\"a\"}}]")
                .templateEnvs("[{\"envName\":\"local\",\"envUrl\":\"http://127.0.0.1:8800\"}]")
                .templateFlows("[{\"flowName\":\"登录\",\"graphJson\":{\"nodes\":[],\"edges\":[]}}]")
                .templatePrompts("[]")
                .enableStatus(1)
                .sortNum(10)
                .build();

        Map<String, Object> pack = codec.toPack(entity);
        JsonNode node = mapper.valueToTree(pack);
        TestProjectTemplate back = codec.fromPack(node);

        // 期望：名称与流还在，formatVersion=1
        assertEquals(1, pack.get("formatVersion"));
        assertEquals("Pack Demo", back.getTemplateName());
        assertTrue(back.getTemplateFlows().contains("登录"));
        assertTrue(back.getTemplateApis().contains("/login"));
        assertEquals(PackKind.FULL, codec.detectKind(node));
        assertEquals(1, codec.summaryOf(back).get("flowCount"));
    }

    @Test
    @Order(2)
    @DisplayName("完整包允许 templateFlows 为空数组")
    void fullPack_emptyFlows_ok() throws Exception {
        // 前提：flows=[]
        String json = """
                {
                  "formatVersion": 1,
                  "templateName": "No Flow Pack",
                  "templateApis": [{"apiName":"登录","apiPath":"/login"}],
                  "templateFlows": []
                }
                """;
        JsonNode node = mapper.readTree(json);

        TestProjectTemplate entity = codec.fromPack(node);

        // 期望：落库 flows 为 []
        assertEquals("[]", entity.getTemplateFlows());
        assertEquals(0, codec.summaryOf(entity).get("flowCount"));
    }

    @Test
    @Order(3)
    @DisplayName("精简 apis 仍可展开且无流")
    void slim_stillExpandable() throws Exception {
        // 前提：冷启动 slim
        String json = """
                {
                  "templateName": "Slim Cold",
                  "authStyle": "bearer",
                  "pathPrefix": ["/api/"],
                  "apis": [{"name":"登录","method":"POST","path":"/login","authMode":"none"}]
                }
                """;
        JsonNode node = mapper.readTree(json);
        assertEquals(PackKind.SLIM, codec.detectKind(node));

        SlimTemplate slim = mapper.treeToValue(node, SlimTemplate.class);
        ExpandResult expanded = expander.expand(slim);

        // 期望：有接口、无流
        assertEquals("[]", expanded.getEntity().getTemplateFlows());
        assertFalse(expanded.getEntity().getTemplateApis().isBlank());
    }

    @Test
    @Order(4)
    @DisplayName("formatVersion 过大则拒绝")
    void formatVersion_tooHigh_fails() throws Exception {
        // 前提：formatVersion=99
        String json = """
                {
                  "formatVersion": 99,
                  "templateName": "Future",
                  "templateApis": [{"apiName":"x","apiPath":"/x"}]
                }
                """;
        JsonNode node = mapper.readTree(json);

        // 期望：抛业务异常
        assertThrows(ServiceException.class, () -> codec.assertFormatVersion(node));
        assertThrows(ServiceException.class, () -> codec.fromPack(node));
    }

    @Test
    @Order(5)
    @DisplayName("缺 formatVersion 视为可导入")
    void formatVersion_missing_ok() throws Exception {
        // 前提：无 formatVersion 的完整包
        String json = """
                {
                  "templateName": "Legacy Pack",
                  "templateApis": [{"apiName":"登录","apiPath":"/login"}]
                }
                """;
        JsonNode node = mapper.readTree(json);

        TestProjectTemplate entity = codec.fromPack(node);

        // 期望：成功
        assertEquals("Legacy Pack", entity.getTemplateName());
        assertEquals(0, codec.summaryOf(entity).get("flowCount"));
    }
}
