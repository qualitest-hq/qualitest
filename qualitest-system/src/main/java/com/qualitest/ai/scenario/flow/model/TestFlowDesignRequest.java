package com.qualitest.ai.scenario.flow.model;

import com.alibaba.fastjson2.JSONArray;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.flow.model.GraphJson;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 测试流 AI 设计接口请求体。
 * <p>
 * graphJson 用于服务端预合并校验与图工具只读摘要，不会整包送入 LLM。
 * composerDoc 用于 user 消息落库，供前端会话恢复时重建 chip。
 */
@Getter
@Setter
public class TestFlowDesignRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 设计模式：空或 project 为项目测试流；template 为项目模板预制流（内联 templateApis，无真实项目成员校验）。
     */
    private String designMode;

    /**
     * designMode=template 时内联的预制接口列表（与模板 templateApis 同形）。
     * 每项可含 testProjectApiId（合成 id，如 tpl-0）、apiPath、requestConfig 等。
     */
    private JSONArray templateApis;

    /**
     * 模板模式下的会话锚点字符串（非雪花 id）；落库 biz_ref 使用。
     */
    private String templateFlowKey;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /** 多轮会话 id；空则服务端创建新会话 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatSessionId;

    /** 由 composerDoc 线性化后的用户描述纯文本 */
    private String prompt;

    /** 从 composerDoc 提取的去重 @ 引用列表 */
    private List<AiDesignMention> mentions;

    /**
     * 编辑器完整文档 JSON，结构含 version 与 nodes。
     * 落库至 user 消息 result_meta_json.composerDoc。
     */
    private Map<String, Object> composerDoc;

    /** 当前画布 graph_json */
    private GraphJson graphJson;

    /**
     * 对话级思考开关：true 开、false 关。
     * 已落库会话以库中 thinking_enabled 为准；草稿会话首次发送时写入新会话。
     */
    private Boolean thinkingEnabled;

    /**
     * 是否开启全自动。
     * true：注入 run_test_flow、追加全自动规程；改图在跑流前/回合结束隐式写库；素材 upsert 直写。
     * false：半自动，Staging 与素材须人审。模板设计模式强制视为 false。
     */
    private Boolean autopilotEnabled;

    /** 是否为模板预制流设计模式 */
    public boolean isTemplateDesignMode() {
        return designMode != null && "template".equalsIgnoreCase(designMode.trim());
    }

    /** 本请求实际是否全自动（模板模式下恒为 false） */
    public boolean isAutopilotEnabledEffective() {
        return Boolean.TRUE.equals(autopilotEnabled) && !isTemplateDesignMode();
    }
}
