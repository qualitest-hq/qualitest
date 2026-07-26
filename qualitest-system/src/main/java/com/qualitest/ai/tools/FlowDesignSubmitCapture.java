package com.qualitest.ai.tools;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import lombok.Getter;

/**
 * 单轮 AI 设计中 {@code submit_flow_design_patch} 工具调用的结果容器。
 * <p>
 * 模型通过 Tool Calling 提交结构化 {@link FlowDesignPatch}，不写入对话正文。
 * 本类在工具执行阶段接收规范化后的 patch 与图校验结果，供编排层组装 API 响应和会话落库。
 * <p>
 * 生命周期：每轮 {@link com.qualitest.ai.scenario.flow.TestFlowDesignRequest} 创建一个新实例，
 * 经 {@link FlowDesignToolContext} 传入工具执行器；Agent 循环结束后读取并丢弃。
 */
@Getter
public class FlowDesignSubmitCapture {

    /** 本轮是否至少调用过一次 submit_flow_design_patch */
    private boolean submitted;

    /** 经 {@link FlowDesignPatchNormalizer} 处理后的 patch（补 id、校验 API 归属、预合并图结构） */
    private FlowDesignPatch normalizedPatch;

    /** 预合并到当前画布副本后的 {@link com.qualitest.flow.validate.GraphJsonValidator} 校验摘要 */
    private DesignValidationResult validation;

    /**
     * 写入一次 submit 工具的规范化结果。
     * <p>
     * 模型在同一轮内多次 submit 时，后一次覆盖前一次，以最终提交为准。
     *
     * @param result Normalizer 输出的 patch 与 validation
     */
    public void record(FlowDesignPatchNormalizer.NormalizeResult result) {
        this.submitted = true;
        this.normalizedPatch = result.patch();
        this.validation = result.validation();
    }
}
