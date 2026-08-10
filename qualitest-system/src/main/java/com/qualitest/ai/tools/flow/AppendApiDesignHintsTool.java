package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.support.TestProjectApiDesignHintsService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向接口 design_hints 追加造流设计提示（直接落库）。
 * <p>
 * 仅 Web 助手可用；用于沉淀业务约定（如 body 二选一），勿写入 apiDescription（导入会覆盖）。
 */
@RequiredArgsConstructor
public class AppendApiDesignHintsTool implements QualitestTool {

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectApiDesignHintsService designHintsService;

    @Override
    public String getName() {
        return FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long apiId = FlowDesignToolSupport.longArg(arguments.get("testProjectApiId"));
        if (apiId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectApiId");
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
        if (api == null || api.getDelStatus() != null && api.getDelStatus() != 0) {
            return FlowDesignToolSupport.errorJson("接口不存在");
        }
        if (api.getTestProjectId() == null || !api.getTestProjectId().equals(ctx.getTestProjectId())) {
            return FlowDesignToolSupport.errorJson("接口不属于当前项目");
        }
        List<String> hints = parseHintsArg(arguments.get("hints"));
        if (hints.isEmpty()) {
            return FlowDesignToolSupport.errorJson("hints 须为非空字符串数组");
        }
        String json = designHintsService.appendHints(
                apiId, hints, TestProjectApiDesignHintsService.SOURCE_AI_DESIGN);
        JSONObject result = new JSONObject();
        result.put("testProjectApiId", String.valueOf(apiId));
        result.put("designHints", TestProjectApiDesignHintsService.readHintList(json));
        result.put("ok", true);
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }

    private static List<String> parseHintsArg(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                String s = arr.getString(i);
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }
}
