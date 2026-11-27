package com.qualitest.project.service.impl;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.validate.FlowGraphValidateResult;
import com.qualitest.flow.validate.FlowGraphValidateService;
import com.qualitest.flow.validate.FlowGraphValidateServiceTestSupport;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.flow.validate.FlowIssueCodes;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationOptions;
import com.qualitest.flow.validate.GraphValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 测谁：测试流保存门禁口径（Stage.SAVE + insert/update 共用的 parse-only 私有方法）。
 * 边界：结构 error / 路由自冲突不拦落盘；仅 JSON 不可解析时拦截。
 * 另：源码护栏——insertTestFlow / updateTestFlow 正文须调用 validateGraphJsonForPersist。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowPersistSavePolicyTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowPersistSavePolicyTest {

    private static final String DIRTY_GRAPH = """
            {"nodes":[
               {"id":"e1","type":"http-entry","name":"入口一","position":{"x":0,"y":0},
                "data":{"method":"POST","path":"/dup","envId":"1",
                        "fallbackOut":{"status":500,"headers":{},"body":{}}}},
               {"id":"e2","type":"http-entry","name":"入口二","position":{"x":100,"y":0},
                "data":{"method":"POST","path":"/dup","envId":"1",
                        "fallbackOut":{"status":500,"headers":{},"body":{}}}},
               {"id":"h1","type":"http","name":"孤立HTTP","position":{"x":200,"y":0},
                "data":{"callMode":"external","externalUrl":"https://example.com","httpMethod":"GET"}}
             ],
             "edges":[]}
            """;

    /**
     * 前提：图含本流路由自冲突 + 无入边业务节点。
     * 期望：完整校验有 error；persist 地板通过；validateGraphJsonForPersist 不抛；
     * SAVE stage 空 issues。
     */
    @Test
    @Order(1)
    @DisplayName("结构错误与路由自冲突不拦保存")
    void persistAllowsGraphErrorsAndRouteSelfConflict() throws Exception {
        GraphJson graph = GraphJson.parse(DIRTY_GRAPH);
        GraphJsonValidator validator = new GraphJsonValidator();
        GraphValidationResult full = validator.validate(graph, GraphValidationOptions.full());
        assertFalse(full.isOk());
        assertTrue(full.getErrors().stream().anyMatch(e -> e.contains("开放 HTTP 入口冲突") || e.contains("无入边")));

        GraphValidationResult floor = validator.validate(graph, GraphValidationOptions.persistMinimal());
        assertTrue(floor.isOk(), () -> "floor errors=" + floor.getErrors());

        FlowGraphValidateService validateService = FlowGraphValidateServiceTestSupport.create(
                validator, mock(FlowRunReadinessGate.class));
        FlowGraphValidateResult save =
                validateService.validate(DIRTY_GRAPH, FlowIssue.Stage.SAVE, 1L, 10L);
        assertFalse(save.hasErrors());
        assertTrue(save.getIssues().isEmpty());

        Method method = TestFlowServiceImpl.class.getDeclaredMethod(
                "validateGraphJsonForPersist", String.class, Long.class, Long.class);
        method.setAccessible(true);
        TestFlowServiceImpl service = new TestFlowServiceImpl();
        assertDoesNotThrow(() -> method.invoke(service, DIRTY_GRAPH, 1L, 10L));
    }

    /**
     * 前提：graph_json 不是合法 JSON 对象。
     * 期望：validateGraphJsonForPersist 抛 ServiceException；SAVE stage 有 GRAPH_JSON_INVALID。
     */
    @Test
    @Order(2)
    @DisplayName("非法 JSON 仍拦保存")
    void persistRejectsUnparseableJson() throws Exception {
        Method method = TestFlowServiceImpl.class.getDeclaredMethod(
                "validateGraphJsonForPersist", String.class, Long.class, Long.class);
        method.setAccessible(true);
        TestFlowServiceImpl service = new TestFlowServiceImpl();
        Exception ex = assertThrows(Exception.class,
                () -> method.invoke(service, "{not-json", 1L, 10L));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause instanceof ServiceException);
        assertTrue(cause.getMessage().contains("无法解析"));

        FlowGraphValidateService validateService = FlowGraphValidateServiceTestSupport.create(
                new GraphJsonValidator(), mock(FlowRunReadinessGate.class));
        FlowGraphValidateResult save =
                validateService.validate("{not-json", FlowIssue.Stage.SAVE, 1L, 10L);
        assertTrue(save.hasErrors());
        assertTrue(save.getIssues().stream()
                .anyMatch(i -> FlowIssueCodes.GRAPH_JSON_INVALID.equals(i.getCode())));
    }

    /**
     * 前提：读取 TestFlowServiceImpl 源码。
     * 期望：insertTestFlow / updateTestFlow 正文仍调用 validateGraphJsonForPersist，
     * 防止保存路径悄悄绕过 parse-only 门禁而反射测试仍绿。
     */
    @Test
    @Order(3)
    @DisplayName("insert/update 源码须调用 validateGraphJsonForPersist")
    void insertAndUpdateMustCallPersistValidate() throws Exception {
        Path src = Path.of("src/main/java/com/qualitest/project/service/impl/TestFlowServiceImpl.java");
        if (!Files.exists(src)) {
            // IDE / 聚合工程可能 cwd 在仓库根
            src = Path.of("qualitest-system/src/main/java/com/qualitest/project/service/impl/TestFlowServiceImpl.java");
        }
        if (!Files.exists(src)) {
            src = Path.of("../qualitest-system/src/main/java/com/qualitest/project/service/impl/TestFlowServiceImpl.java");
        }
        assertTrue(Files.exists(src), "找不到 TestFlowServiceImpl.java：" + src.toAbsolutePath());
        String text = Files.readString(src, StandardCharsets.UTF_8);
        int insertIdx = text.indexOf("public int insertTestFlow");
        int updateIdx = text.indexOf("public int updateTestFlow");
        assertTrue(insertIdx > 0 && updateIdx > insertIdx);
        String insertBody = text.substring(insertIdx, updateIdx);
        String updateBody = text.substring(updateIdx, Math.min(text.length(), updateIdx + 3500));
        assertTrue(insertBody.contains("validateGraphJsonForPersist("),
                "insertTestFlow 未调用 validateGraphJsonForPersist");
        assertTrue(updateBody.contains("validateGraphJsonForPersist("),
                "updateTestFlow 未调用 validateGraphJsonForPersist");
    }

}
