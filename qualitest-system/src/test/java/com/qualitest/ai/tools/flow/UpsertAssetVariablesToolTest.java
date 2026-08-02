package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

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
 * 测 UpsertAssetVariablesTool：按 key 新建或更新项目素材，回执不含明文。
 * 边界：Mock ITestProjectAssetService，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=UpsertAssetVariablesToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpsertAssetVariablesToolTest {

    private static final Long PROJECT_ID = 100L;

    private ITestProjectAssetService assetService;
    private UpsertAssetVariablesTool tool;
    private FlowDesignToolContext ctx;

    @BeforeEach
    void setUp() {
        assetService = mock(ITestProjectAssetService.class);
        tool = new UpsertAssetVariablesTool(assetService);
        ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .maxToolResultBytes(8192)
                .build();
    }

    /**
     * 前提：按 key 查询抛「素材条目不存在」；insert 回传入参资产。
     * 期望：走 insert；回执 action=created、含字段名；JSON 无口令与手机号明文。
     */
    @Test
    @Order(1)
    @DisplayName("无既有 key 时 insert，回执无明文")
    void upsert_createsWhenMissing() {
        when(assetService.selectTestProjectAssetResultByKey(PROJECT_ID, "clientAuth"))
                .thenThrow(new ServiceException("素材条目不存在"));
        when(assetService.insertTestProjectAsset(any())).thenAnswer(inv -> {
            TestProjectAssetSaveParams p = inv.getArgument(0);
            return TestProjectAssetResult.builder()
                    .id(9L)
                    .testProjectId(PROJECT_ID)
                    .key(p.getKey())
                    .remark(p.getRemark())
                    .assets(p.getAssets())
                    .build();
        });

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
        JSONArray fieldNames = root.getJSONArray("fields");
        assertTrue(fieldNames.contains("mobile"));
        assertTrue(fieldNames.contains("password"));
        assertFalse(json.contains("Test@123456"));
        assertFalse(json.contains("13800000001"));

        ArgumentCaptor<TestProjectAssetSaveParams> cap = ArgumentCaptor.forClass(TestProjectAssetSaveParams.class);
        verify(assetService).insertTestProjectAsset(cap.capture());
        verify(assetService, never()).updateTestProjectAsset(any());
        assertEquals("clientAuth", cap.getValue().getKey());
        assertEquals(Map.of("mobile", "13800000001", "password", "Test@123456"),
                ((Map<?, ?>) cap.getValue().getAssets().get("clientAuth")));
    }

    /**
     * 前提：按 key 已查到 id=9 的条目；update 回传入参。
     * 期望：走 update（带原 id）；action=updated；返回 JSON 无新旧口令明文。
     */
    @Test
    @Order(2)
    @DisplayName("已有 key 时 update，回执无明文")
    void upsert_updatesWhenExists() {
        when(assetService.selectTestProjectAssetResultByKey(PROJECT_ID, "clientAuth"))
                .thenReturn(TestProjectAssetResult.builder()
                        .id(9L)
                        .testProjectId(PROJECT_ID)
                        .key("clientAuth")
                        .remark("旧备注")
                        .assets(Map.of("clientAuth", Map.of("password", "OLD_PLAINTEXT_LEAK")))
                        .build());
        when(assetService.updateTestProjectAsset(any())).thenAnswer(inv -> {
            TestProjectAssetSaveParams p = inv.getArgument(0);
            return TestProjectAssetResult.builder()
                    .id(p.getId())
                    .testProjectId(PROJECT_ID)
                    .key(p.getKey())
                    .remark(p.getRemark())
                    .assets(p.getAssets())
                    .build();
        });

        String json = tool.execute(Map.of(
                "key", "clientAuth",
                "fields", Map.of("password", "NEW_PLAINTEXT_LEAK")), ctx);

        JSONObject root = JSON.parseObject(json);
        assertEquals("updated", root.getString("action"));
        assertFalse(json.contains("NEW_PLAINTEXT_LEAK"));
        assertFalse(json.contains("OLD_PLAINTEXT_LEAK"));

        ArgumentCaptor<TestProjectAssetSaveParams> cap = ArgumentCaptor.forClass(TestProjectAssetSaveParams.class);
        verify(assetService).updateTestProjectAsset(cap.capture());
        assertEquals(9L, cap.getValue().getId());
        verify(assetService, never()).insertTestProjectAsset(any());
    }

    /**
     * 前提：工具名 upsert_asset_variables。
     * 期望：MCP 不允许调用；Web 助手可见。
     */
    @Test
    @Order(3)
    @DisplayName("upsert 仅 Web 可见、MCP 不可调用")
    void mcpDisallowsUpsert() {
        String id = FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId();

        assertFalse(FlowDesignToolExecutor.isMcpAllowedTool(id));
        assertTrue(FlowDesignToolNames.isWebAgent(id));
    }
}
