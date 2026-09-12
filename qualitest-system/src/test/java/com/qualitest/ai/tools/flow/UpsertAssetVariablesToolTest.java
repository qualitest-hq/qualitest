package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.AssetUpsertCapture;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
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
 * 测 UpsertAssetVariablesTool：半自动记提案；全自动直接写库；回执不含明文。
 * 边界：Mock 素材服务，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=UpsertAssetVariablesToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpsertAssetVariablesToolTest {

    private static final Long PROJECT_ID = 100L;

    private ITestProjectAssetService assetService;
    private UpsertAssetVariablesTool tool;
    private AssetUpsertCapture capture;
    private FlowDesignToolContext ctx;

    @BeforeEach
    void setUp() {
        assetService = mock(ITestProjectAssetService.class);
        tool = new UpsertAssetVariablesTool(assetService);
        capture = new AssetUpsertCapture();
        ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .assetUpsertCapture(capture)
                .build();
    }

    /**
     * 前提：按 key 查询抛「素材条目不存在」。
     * 期望：不调 insert；Capture 有 pending created 提案；回执 status=pending、无明文。
     */
    @Test
    @Order(1)
    @DisplayName("无既有 key 时记 created 提案，不落盘")
    void upsert_recordsCreatedProposalWhenMissing() {
        when(assetService.selectTestProjectAssetResultByKey(PROJECT_ID, "clientAuth"))
                .thenThrow(new ServiceException("素材条目不存在"));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("mobile", "13800000001");
        fields.put("password", "Test@123456");

        String json = tool.execute(Map.of(
                "key", "clientAuth",
                "remark", "客户端登录",
                "fields", fields), ctx);

        JSONObject root = JSON.parseObject(json);
        assertEquals("clientAuth", root.getString("key"));
        assertEquals("created", root.getString("action"));
        assertEquals(AssetUpsertProposal.STATUS_PENDING, root.getString("status"));
        JSONArray fieldNames = root.getJSONArray("fields");
        assertTrue(fieldNames.contains("mobile"));
        assertTrue(fieldNames.contains("password"));
        assertFalse(json.contains("Test@123456"));
        assertFalse(json.contains("13800000001"));

        verify(assetService, never()).insertTestProjectAsset(any());
        verify(assetService, never()).updateTestProjectAsset(any());
        assertTrue(capture.hasProposals());
        AssetUpsertProposal proposal = capture.getProposals().get(0);
        assertEquals("clientAuth", proposal.getKey());
        assertEquals(AssetUpsertProposal.ACTION_CREATED, proposal.getAction());
        assertEquals("Test@123456", proposal.getFields().get("password"));
    }

    /**
     * 前提：按 key 已查到条目。
     * 期望：不调 update；Capture 有 pending updated 提案；回执无明文。
     */
    @Test
    @Order(2)
    @DisplayName("已有 key 时记 updated 提案，不落盘")
    void upsert_recordsUpdatedProposalWhenExists() {
        when(assetService.selectTestProjectAssetResultByKey(PROJECT_ID, "clientAuth"))
                .thenReturn(TestProjectAssetResult.builder()
                        .id(9L)
                        .testProjectId(PROJECT_ID)
                        .key("clientAuth")
                        .remark("旧备注")
                        .assets(Map.of("clientAuth", Map.of("password", "OLD_PLAINTEXT_LEAK")))
                        .build());

        String json = tool.execute(Map.of(
                "key", "clientAuth",
                "fields", Map.of("password", "NEW_PLAINTEXT_LEAK")), ctx);

        JSONObject root = JSON.parseObject(json);
        assertEquals("updated", root.getString("action"));
        assertEquals(AssetUpsertProposal.STATUS_PENDING, root.getString("status"));
        assertFalse(json.contains("NEW_PLAINTEXT_LEAK"));
        assertFalse(json.contains("OLD_PLAINTEXT_LEAK"));

        verify(assetService, never()).insertTestProjectAsset(any());
        verify(assetService, never()).updateTestProjectAsset(any());
        assertEquals(AssetUpsertProposal.ACTION_UPDATED, capture.getProposals().get(0).getAction());
    }

    /**
     * 前提：全自动、按 key 查询抛「不存在」。
     * 期望：调 insert；Capture status=confirmed；回执无明文。
     */
    @Test
    @Order(3)
    @DisplayName("全自动无既有 key 时直接 insert")
    void upsert_autopilot_persistsCreated() {
        when(assetService.selectTestProjectAssetResultByKey(PROJECT_ID, "clientAuth"))
                .thenThrow(new ServiceException("素材条目不存在"));
        FlowDesignToolContext autoCtx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .assetUpsertCapture(capture)
                .autopilotEnabled(true)
                .build();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("mobile", "13800000001");
        fields.put("password", "Test@123456");

        String json = tool.execute(Map.of(
                "key", "clientAuth",
                "remark", "客户端登录",
                "fields", fields), autoCtx);

        JSONObject root = JSON.parseObject(json);
        assertEquals(AssetUpsertProposal.STATUS_CONFIRMED, root.getString("status"));
        assertEquals("created", root.getString("action"));
        assertFalse(json.contains("Test@123456"));
        verify(assetService).insertTestProjectAsset(any());
        verify(assetService, never()).updateTestProjectAsset(any());
        assertEquals(AssetUpsertProposal.STATUS_CONFIRMED, capture.getProposals().get(0).getStatus());
    }

    /**
     * 前提：工具名 upsert_asset_variables。
     * 期望：MCP 不允许调用；Web 助手可见。
     */
    @Test
    @Order(4)
    @DisplayName("upsert 仅 Web 可见、MCP 不可调用")
    void mcpDisallowsUpsert() {
        String id = FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();

        assertFalse(FlowDesignToolExecutor.isMcpAllowedTool(id));
        assertTrue(FlowDesignToolNames.isWebAgent(id));
    }

    /**
     * 前提：上下文无 Capture。
     * 期望：回执含 error，不落盘。
     */
    @Test
    @Order(5)
    @DisplayName("缺少 Capture 时返回错误")
    void upsert_missingCapture_returnsError() {
        FlowDesignToolContext noCapture = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .build();
        String json = tool.execute(Map.of(
                "key", "clientAuth",
                "fields", Map.of("password", "x")), noCapture);
        assertTrue(JSON.parseObject(json).containsKey("error"));
        verify(assetService, never()).insertTestProjectAsset(any());
    }
}
