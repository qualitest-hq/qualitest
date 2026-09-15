package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSONArray;
import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.flow.model.GraphJson;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 测试流 AI 设计工具执行时的请求级上下文。
 * <p>
 * Web 造流与 MCP 共用：项目、测试流、基准画布、检索范围、结果字节上限，
 * 以及本轮 submit 累积器、素材/鉴权提案累积器、短名映射、内存工作图。
 */
@Getter
@Builder
public class FlowDesignToolContext {

    /** 当前测试项目 id，用于 API 归属校验与项目内检索 */
    private final Long testProjectId;

    /** 当前测试流 id，用于 Run 失败查询时的归属校验 */
    private final Long testFlowId;

    /** 设计模式：空/project 为项目流；template 为模板预制流 */
    private final String designMode;

    /** 模板模式下内联的预制接口列表；项目模式为 null */
    private final JSONArray templateApis;

    /**
     * 用户请求带来的基准画布（本轮开始时的图）。
     * Web 由设计面板传入；MCP 可由信封或 get_flow 写入。
     */
    private final GraphJson graphJson;

    /**
     * 本轮已接受 submit 单元合并后的工作图。
     * 每次单元校验成功后更新；只读查图工具优先读这里，使后续步骤能看到本轮已搭内容。
     */
    @Builder.Default
    private final AtomicReference<GraphJson> workingGraphRef = new AtomicReference<>();

    /** 用户在面板 @ 选中的 API id；search_apis 优先在此范围匹配 */
    private final List<Long> scopeApiIds;

    /** 用户在画布选中的节点 id；供提示模型先查这些节点详情 */
    private final List<String> contextNodeIds;

    /** 「AI 修复」入口带入的失败 Run id；get_run_failure 未显式传 runId 时回退使用 */
    private final Long contextRunId;

    /**
     * 是否开启全自动。
     * true：允许 run_test_flow；素材 upsert 直写；改图由跑流前/回合结束隐式写库。
     * false：半自动，改图进 Staging、素材须聊天侧确认。
     */
    @Builder.Default
    private final boolean autopilotEnabled = false;

    /**
     * 本轮是否已至少成功隐式落盘一次（写过 test_flow）。
     */
    @Builder.Default
    private final AtomicBoolean committedThisTurn = new AtomicBoolean(false);

    /**
     * 隐式落盘成功后的回调（参数：testFlowId、已落库图）。
     * 设计流式通道可据此推送 graphCommitted；可为 null。
     */
    private final BiConsumer<Long, GraphJson> onGraphCommitted;

    /**
     * 已触发 Run 并拿到 runId 后的回调。
     * 设计流式通道可据此推送 runStarted，画布开始按步骤高亮；可为 null。
     */
    private final Consumer<Long> onRunStarted;

    /**
     * 设计请求带来的默认运行场景 id。
     * run_test_flow 未在工具参数里传 runScenarioId 时用此值。
     */
    private final String defaultRunScenarioId;

    /**
     * 设计请求带来的默认环境 id。
     * run_test_flow 未在工具参数里传 testProjectEnvId 时用此值。
     */
    private final Long defaultTestProjectEnvId;

    /** search_apis 单次返回条数上限 */
    @Builder.Default
    private final int maxSearchApis = AiLlmConfigService.DEFAULT_MAX_SEARCH_APIS;

    /** list_flows / list_subflow_templates 单次返回条数上限 */
    @Builder.Default
    private final int maxListFlows = AiLlmConfigService.DEFAULT_MAX_LIST_FLOWS;

    /** 只读工具返回 JSON 字节上限；超出时附 truncated 与 hint */
    @Builder.Default
    private final int maxToolResultBytes = AiLlmConfigService.DEFAULT_MAX_TOOL_RESULT_BYTES;

    /**
     * 本轮 submit_* 成功单元的累积器。
     * 编排层每轮新建；可为 null（如纯 MCP 读图，无提交）。
     */
    private final FlowDesignSubmitCapture submitCapture;

    /**
     * 本轮 upsert_asset_variables 提案累积器。
     * 半自动只记提案；全自动工具内已写库后记 confirmed；为空时 upsert 工具直接报错。
     */
    private final AssetUpsertCapture assetUpsertCapture;

    /**
     * 本轮 upsert_auth_profile 提案累积器。
     * 半自动只记提案；全自动已写库后记 confirmed；为空时 upsert 工具直接报错。
     */
    private final AuthProfileUpsertCapture authProfileUpsertCapture;

    /** 当前 AI 会话 id；用于把短名映射落会话。MCP 可空 */
    private final Long aiChatSessionId;

    /**
     * 会话级短名→雪花 id 映射（可变 Map）。
     * 单单元规范化时读写，成功后由提交逻辑落会话。
     */
    @Builder.Default
    private final Map<String, String> flowDesignClientIdMap = new HashMap<>();

    /** 是否为模板预制流设计模式 */
    public boolean isTemplateDesignMode() {
        return designMode != null && "template".equalsIgnoreCase(designMode.trim());
    }

    /**
     * 工具实际读写的画布：有工作图用工作图，否则用基准 graphJson。
     */
    public GraphJson resolveGraphJson() {
        GraphJson working = workingGraphRef != null ? workingGraphRef.get() : null;
        return working != null ? working : graphJson;
    }

    /** 单元 submit 成功后写入新的内存工作图（不写业务库） */
    public void advanceWorkingGraph(GraphJson next) {
        if (workingGraphRef != null && next != null) {
            workingGraphRef.set(next);
        }
    }

    /** 标记本轮已成功隐式落盘，并调用 onGraphCommitted（若有） */
    public void notifyGraphCommitted(GraphJson saved) {
        if (committedThisTurn != null) {
            committedThisTurn.set(true);
        }
        if (onGraphCommitted != null && testFlowId != null && saved != null) {
            onGraphCommitted.accept(testFlowId, saved);
        }
    }

    /** 已拿到 runId 后通知画布开始按步骤高亮（若有 onRunStarted） */
    public void notifyRunStarted(Long runId) {
        if (onRunStarted != null && runId != null) {
            onRunStarted.accept(runId);
        }
    }

    /** 本轮是否已成功隐式落盘过 */
    public boolean hasCommittedThisTurn() {
        return committedThisTurn != null && committedThisTurn.get();
    }

    /**
     * 半自动未确认提案的拦截原因；无 pending 时返回 null。
     * 素材提案优先于鉴权 Profile 提案。
     */
    public String pendingUpsertBlockReason() {
        if (assetUpsertCapture != null && assetUpsertCapture.hasPendingProposals()) {
            return "尚有未确认的素材库提案";
        }
        if (authProfileUpsertCapture != null && authProfileUpsertCapture.hasPendingProposals()) {
            return "尚有未确认的鉴权 Profile 提案";
        }
        return null;
    }
}
