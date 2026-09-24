package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.support.TestProjectVariableEntrySupport;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 新增或更新项目测试环境实体（名称、Base URL、环境变量、共享状态、破坏性还原开关等）。
 * <p>
 * 写入的是环境库记录本身，不修改画布上的运行场景绑定。
 * 调用成功即落库，无 Staging；模板画布禁止调用。
 * 经 MCP 调用时须项目已开启「允许 MCP 自动写流」。
 */
@RequiredArgsConstructor
public class UpsertProjectEnvTool implements QualitestTool {

    /** 环境名命中该模式时强制禁止正式 Run 做破坏性还原 */
    private static final Pattern PRODUCTION_NAME = Pattern.compile("prod|生产|production", Pattern.CASE_INSENSITIVE);

    private final ITestProjectEnvService testProjectEnvService;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPSERT_PROJECT_ENV.getId();
    }

    /**
     * 解析参数后新建或更新环境。
     * <ul>
     *   <li>新建：{@code create=true}，且须 {@code envName}、操作者用户 id</li>
     *   <li>更新：须 {@code testProjectEnvId}；仅覆盖本次传入的非空字段；
     *       {@code envVariables} 显式传入（含空数组）才改变量</li>
     * </ul>
     * 回执含环境 id、名称、URL、变量键名（不含值）与写库提示。
     *
     * @param arguments 工具参数
     * @param ctx       须含 testProjectId；新建还须 operatorUserId
     * @return 成功回执或顶层含 error 的 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布不支持写入项目环境");
        }
        Long projectId = ctx != null ? ctx.getTestProjectId() : null;
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        Long envId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("testProjectEnvId") : null);
        boolean create = Boolean.TRUE.equals(arguments != null ? arguments.get("create") : null)
                || "true".equalsIgnoreCase(FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("create") : null));
        if (envId == null && !create) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectEnvId；新建须 create=true");
        }
        // 同时传了 id 时按更新处理，忽略 create
        if (envId != null) {
            create = false;
        }

        String envName = FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("envName") : null);
        String envUrl = FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("envUrl") : null);
        String shareStatus = FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("shareStatus") : null);
        String envColor = FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("envColor") : null);
        Integer allowDestructive = FlowDesignToolSupport.intArg(
                arguments != null ? arguments.get("allowDestructiveReset") : null);
        String envVariablesJson;
        try {
            envVariablesJson = normalizeEnvVariablesArg(
                    arguments != null ? arguments.get("envVariables") : null);
        } catch (ServiceException e) {
            return FlowDesignToolSupport.errorJson(
                    e.getMessage() != null ? e.getMessage() : "envVariables 格式无效");
        }

        try {
            // 标记变更来源，供环境变更推送区分 MCP / Web 全自动
            FlowExternalChangeSourceHolder.set(
                    FlowExternalChangeSourceHolder.mcpOrWebAutopilot(
                            ctx != null && ctx.getAiChatSessionId() != null));
            if (create) {
                return createEnv(projectId, ctx != null ? ctx.getOperatorUserId() : null,
                        envName, envUrl, shareStatus, envColor, allowDestructive, envVariablesJson);
            }
            return updateEnv(projectId, envId, envName, envUrl, shareStatus, envColor,
                    allowDestructive, envVariablesJson);
        } catch (ServiceException e) {
            return FlowDesignToolSupport.errorJson(
                    e.getMessage() != null ? e.getMessage() : "写入项目环境失败");
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("写入项目环境异常: " + e.getMessage());
        } finally {
            FlowExternalChangeSourceHolder.clear();
        }
    }

    /** 插入新环境；归属当前操作者，默认私有共享状态 */
    private String createEnv(Long projectId, Long operatorUserId,
                             String envName, String envUrl, String shareStatus, String envColor,
                             Integer allowDestructive, String envVariablesJson) {
        if (envName.isEmpty()) {
            return FlowDesignToolSupport.errorJson("新建须提供 envName");
        }
        if (operatorUserId == null) {
            return FlowDesignToolSupport.errorJson("缺少操作者；请确认已登录或 Project Token 已绑定用户");
        }
        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectId(projectId)
                .userId(operatorUserId)
                .envName(envName)
                .envUrl(envUrl.isEmpty() ? null : envUrl)
                .shareStatus(shareStatus.isEmpty()
                        ? TestProjectConstants.DEFAULT_ENV_SHARE_STATUS : shareStatus)
                .envColor(envColor.isEmpty() ? null : envColor)
                .envVariables(envVariablesJson != null
                        ? envVariablesJson : TestProjectConstants.EMPTY_ENV_VARIABLES_JSON)
                .allowDestructiveReset(resolveAllowDestructive(envName, allowDestructive, 0))
                .delStatus(0)
                .build();
        testProjectEnvService.insertTestProjectEnv(env);
        return ack("created", env);
    }

    /**
     * 浅合并更新已有环境：仅写入本次非空的名称/URL/共享/颜色；
     * envVariables 为 null 表示不改，非 null（含空数组规范结果）表示覆盖。
     */
    private String updateEnv(Long projectId, Long envId,
                             String envName, String envUrl, String shareStatus, String envColor,
                             Integer allowDestructive, String envVariablesJson) {
        TestProjectEnv existing = testProjectEnvService.selectTestProjectEnvById(envId);
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
            return FlowDesignToolSupport.errorJson("测试环境不存在: " + envId);
        }
        if (!projectId.equals(existing.getTestProjectId())) {
            return FlowDesignToolSupport.errorJson("环境与当前项目不属于同一项目");
        }
        if (!envName.isEmpty()) {
            existing.setEnvName(envName);
        }
        if (!envUrl.isEmpty()) {
            existing.setEnvUrl(envUrl);
        }
        if (!shareStatus.isEmpty()) {
            existing.setShareStatus(shareStatus);
        }
        if (!envColor.isEmpty()) {
            existing.setEnvColor(envColor);
        }
        if (envVariablesJson != null) {
            existing.setEnvVariables(envVariablesJson);
        }
        String nameForGuard = !envName.isEmpty() ? envName : existing.getEnvName();
        existing.setAllowDestructiveReset(resolveAllowDestructive(
                nameForGuard, allowDestructive, existing.getAllowDestructiveReset()));
        testProjectEnvService.updateTestProjectEnv(existing);
        return ack("updated", existing);
    }

    /** 组装成功回执；变量只回键名，不含明文值 */
    private static String ack(String action, TestProjectEnv env) {
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("action", action);
        result.put("testProjectEnvId", String.valueOf(env.getTestProjectEnvId()));
        result.put("envName", env.getEnvName());
        result.put("envUrl", env.getEnvUrl());
        result.put("shareStatus", env.getShareStatus());
        result.put("allowDestructiveReset", env.getAllowDestructiveReset() != null
                ? env.getAllowDestructiveReset() : 0);
        result.put("envVarKeys", TestProjectVariableEntrySupport.extractVariableKeys(env.getEnvVariables()));
        result.put("hint", "环境已写库；场景绑定请 submit_scenario(testProjectEnvId=…)，"
                + "或 run_test_flow 显式传 testProjectEnvId");
        return result.toJSONString();
    }

    /**
     * 将工具入参规范为可入库的环境变量 JSON。
     * <ul>
     *   <li>null：未传，更新时表示不改该字段</li>
     *   <li>空串 / 空数组：清空为 {@code []}</li>
     *   <li>字符串或数组：校验并规范为变量条目数组后再入库</li>
     * </ul>
     */
    static String normalizeEnvVariablesArg(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return TestProjectConstants.EMPTY_ENV_VARIABLES_JSON;
            }
            return TestProjectVariableEntrySupport.normalizeVariablesJsonForPersist(s.trim());
        }
        if (raw instanceof JSONArray || raw instanceof Iterable<?> || raw.getClass().isArray()) {
            String json = JSON.toJSONString(raw);
            return TestProjectVariableEntrySupport.normalizeVariablesJsonForPersist(json);
        }
        throw new ServiceException(
                "envVariables 须为条目数组，如 [{\"key\":\"db\",\"assets\":{\"db\":{\"host\":\"...\"}}}]");
    }

    /**
     * 计算 allowDestructiveReset：名称像生产环境时强制 0；
     * 否则用本次请求值（仅认 0/1），未传则沿用 fallback。
     */
    private static int resolveAllowDestructive(String envName, Integer requested, Integer fallback) {
        if (isProductionName(envName)) {
            return 0;
        }
        if (requested != null) {
            return requested == 1 ? 1 : 0;
        }
        return fallback != null ? fallback : 0;
    }

    /** 环境名是否按生产环境处理（含 prod / 生产 / production，忽略大小写） */
    private static boolean isProductionName(String envName) {
        return envName != null && PRODUCTION_NAME.matcher(envName).find();
    }
}
