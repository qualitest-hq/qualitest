package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestProjectEnvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 UpsertProjectEnvTool：新建与更新项目环境实体。
 * 覆盖缺参拒绝、create 落库、按 id 浅合并更新、生产环境名强制关闭破坏性还原、envVariables 写入。
 * 边界：Mock ITestProjectEnvService，无真实数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=UpsertProjectEnvToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpsertProjectEnvToolTest {

    private static final Long PROJECT_ID = 200L;
    private static final Long USER_ID = 9L;

    private ITestProjectEnvService envService;
    private UpsertProjectEnvTool tool;

    @BeforeEach
    void setUp() {
        envService = mock(ITestProjectEnvService.class);
        tool = new UpsertProjectEnvTool(envService);
    }

    private FlowDesignToolContext ctx() {
        return FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .operatorUserId(USER_ID)
                .autopilotEnabled(true)
                .maxToolResultBytes(8192)
                .build();
    }

    /**
     * 前提：未传 create / id。
     * 期望：报错且不写库。
     */
    @Test
    @Order(1)
    @DisplayName("缺少 create 与 id 时拒绝")
    void reject_whenMissingCreateAndId() {
        String json = tool.execute(Map.of("envName", "联调"), ctx());
        assertTrue(JSON.parseObject(json).getString("error").contains("create"));
        verify(envService, never()).insertTestProjectEnv(any());
    }

    /**
     * 前提：create=true、envName、envUrl。
     * 期望：insert 并回执 created。
     */
    @Test
    @Order(2)
    @DisplayName("create=true 时新建环境")
    void create_whenCreateTrue() {
        when(envService.insertTestProjectEnv(any())).thenAnswer(inv -> {
            TestProjectEnv e = inv.getArgument(0);
            e.setTestProjectEnvId(9001L);
            return 1;
        });
        String json = tool.execute(Map.of(
                "create", true,
                "envName", "联调",
                "envUrl", "http://localhost:8080"), ctx());
        JSONObject root = JSON.parseObject(json);
        assertFalse(root.containsKey("error"));
        assertEquals("created", root.getString("action"));
        assertEquals("9001", root.getString("testProjectEnvId"));
        ArgumentCaptor<TestProjectEnv> cap = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(envService).insertTestProjectEnv(cap.capture());
        assertEquals(PROJECT_ID, cap.getValue().getTestProjectId());
        assertEquals(USER_ID, cap.getValue().getUserId());
        assertEquals("联调", cap.getValue().getEnvName());
    }

    /**
     * 前提：已有环境，传 testProjectEnvId 与新名称。
     * 期望：update 并回执 updated。
     */
    @Test
    @Order(3)
    @DisplayName("按 id 更新环境名称")
    void update_byId() {
        TestProjectEnv existing = TestProjectEnv.builder()
                .testProjectEnvId(9001L)
                .testProjectId(PROJECT_ID)
                .userId(USER_ID)
                .envName("旧名")
                .envUrl("http://old")
                .delStatus(0)
                .allowDestructiveReset(0)
                .build();
        when(envService.selectTestProjectEnvById(9001L)).thenReturn(existing);
        when(envService.updateTestProjectEnv(any())).thenReturn(1);

        String json = tool.execute(Map.of(
                "testProjectEnvId", "9001",
                "envName", "新名"), ctx());
        JSONObject root = JSON.parseObject(json);
        assertEquals("updated", root.getString("action"));
        assertEquals("新名", root.getString("envName"));
        verify(envService).updateTestProjectEnv(existing);
        assertEquals("新名", existing.getEnvName());
    }

    /**
     * 前提：名称含「生产」。
     * 期望：allowDestructiveReset 强制为 0。
     */
    @Test
    @Order(4)
    @DisplayName("生产环境名强制禁止破坏性还原")
    void create_productionForcesAllowDestructiveOff() {
        when(envService.insertTestProjectEnv(any())).thenAnswer(inv -> {
            TestProjectEnv e = inv.getArgument(0);
            e.setTestProjectEnvId(1L);
            return 1;
        });
        tool.execute(Map.of(
                "create", true,
                "envName", "生产环境",
                "allowDestructiveReset", 1), ctx());
        ArgumentCaptor<TestProjectEnv> cap = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(envService).insertTestProjectEnv(cap.capture());
        assertEquals(0, cap.getValue().getAllowDestructiveReset());
    }

    /**
     * 前提：envVariables 为合法条目数组。
     * 期望：normalize 后写入。
     */
    @Test
    @Order(5)
    @DisplayName("envVariables 条目写入")
    void create_withEnvVariables() {
        when(envService.insertTestProjectEnv(any())).thenAnswer(inv -> {
            TestProjectEnv e = inv.getArgument(0);
            e.setTestProjectEnvId(2L);
            return 1;
        });
        String json = tool.execute(Map.of(
                "create", true,
                "envName", "联调",
                "envVariables", List.of(Map.of(
                        "key", "db",
                        "assets", Map.of("db", Map.of("host", "127.0.0.1"))))), ctx());
        JSONObject root = JSON.parseObject(json);
        assertFalse(root.containsKey("error"), () -> "unexpected error: " + json);
        assertTrue(root.getJSONArray("envVarKeys").contains("db"));
        ArgumentCaptor<TestProjectEnv> cap = ArgumentCaptor.forClass(TestProjectEnv.class);
        verify(envService).insertTestProjectEnv(cap.capture());
        assertTrue(cap.getValue().getEnvVariables().contains("db"));
    }
}
