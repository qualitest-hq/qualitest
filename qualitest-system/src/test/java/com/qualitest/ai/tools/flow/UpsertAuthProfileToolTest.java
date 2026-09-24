package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.AuthProfileUpsertCapture;
import com.qualitest.ai.tools.AuthProfileUpsertProposal;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
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
 * 测 UpsertAuthProfileTool：半自动须有捕获器并记提案；全自动可无捕获器并直接写库。
 * 边界：Mock TestProjectMapper，无真实数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=UpsertAuthProfileToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpsertAuthProfileToolTest {

    private static final Long PROJECT_ID = 200L;

    private TestProjectMapper testProjectMapper;
    private UpsertAuthProfileTool tool;
    private AuthProfileUpsertCapture capture;

    @BeforeEach
    void setUp() {
        testProjectMapper = mock(TestProjectMapper.class);
        tool = new UpsertAuthProfileTool(testProjectMapper);
        capture = new AuthProfileUpsertCapture();
    }

    private static Map<String, Object> createPatch() {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("name", "管理端 Bearer");
        patch.put("headerName", "Authorization");
        patch.put("headerValueTemplate", "Bearer {{asset.adminAuth.token}}");
        return patch;
    }

    /**
     * 前提：半自动、无 Capture。
     * 期望：回执含 error，不 update 项目。
     */
    @Test
    @Order(1)
    @DisplayName("半自动缺少 Capture 时返回错误")
    void upsert_missingCapture_returnsError() {
        FlowDesignToolContext noCapture = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .build();
        String json = tool.execute(Map.of(
                "create", true,
                "patch", createPatch()), noCapture);
        assertTrue(JSON.parseObject(json).containsKey("error"));
        assertTrue(JSON.parseObject(json).getString("error").contains("捕获器未就绪"));
        verify(testProjectMapper, never()).updateTestProject(any());
    }

    /**
     * 前提：半自动有 Capture、项目 auth 为空、create=true。
     * 期望：不落盘；Capture pending created。
     */
    @Test
    @Order(2)
    @DisplayName("半自动新建记 pending 提案")
    void upsert_recordsPendingWhenSemiAuto() {
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(TestProject.builder()
                .testProjectId(PROJECT_ID)
                .authConfig("{}")
                .build());
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .authProfileUpsertCapture(capture)
                .build();

        String json = tool.execute(Map.of(
                "create", true,
                "patch", createPatch()), ctx);

        JSONObject root = JSON.parseObject(json);
        assertEquals(AuthProfileUpsertProposal.STATUS_PENDING, root.getString("status"));
        assertEquals(AuthProfileUpsertProposal.ACTION_CREATED, root.getString("action"));
        verify(testProjectMapper, never()).updateTestProject(any());
        assertTrue(capture.hasProposals());
    }

    /**
     * 前提：全自动、Capture 为空、create=true。
     * 期望：updateTestProject 落盘；回执 confirmed。
     */
    @Test
    @Order(3)
    @DisplayName("全自动无 Capture 时仍直写")
    void upsert_autopilot_withoutCapture_persists() {
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(TestProject.builder()
                .testProjectId(PROJECT_ID)
                .authConfig("{}")
                .build());
        when(testProjectMapper.updateTestProject(any())).thenReturn(1);
        FlowDesignToolContext autoNoCapture = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .autopilotEnabled(true)
                .build();

        String json = tool.execute(Map.of(
                "create", true,
                "patch", createPatch()), autoNoCapture);

        JSONObject root = JSON.parseObject(json);
        assertFalse(root.containsKey("error"));
        assertEquals(AuthProfileUpsertProposal.STATUS_CONFIRMED, root.getString("status"));
        assertEquals(AuthProfileUpsertProposal.ACTION_CREATED, root.getString("action"));
        assertTrue(root.getString("profileId") != null && !root.getString("profileId").isBlank());
        verify(testProjectMapper).updateTestProject(any());
    }
}
