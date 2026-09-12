package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphJson;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 单条 Staging 单元确认接口的响应体。
 * <p>
 * ok=true 时 graphJson 为合并该单元后的完整图，前端用其写回编辑态；
 * ok=false 时 graphJson 为 null，前端不得落盘该单元。
 */
@Getter
@Builder
public class FlowDesignPatchConfirmResult {

    /** true 表示 errors 为空，允许将该单元写入正式图层 */
    private final boolean ok;

    /** 阻断确认的问题（依赖未满足、图结构错误、本单元断言路径错误等） */
    private final List<String> errors;

    /** 不阻断确认的提示（含末单元附带的保存风险文案） */
    private final List<String> warnings;

    /**
     * 确认该单元后的完整 graph_json。
     * 依赖未满足或校验失败时为 null。
     */
    private final GraphJson graphJson;

    /** 依赖未满足时的人类可读提示，例如须先确认某 addNode 再确认 addEdge */
    private final List<String> dependencyHints;

    /**
     * 本轮已无待确认/待拒绝单元时附带的保存风险列表：
     * 鉴权凭证来源缺失、登录抽取缺失、HTTP 必填测值缺失等。
     * 不阻断本次确认；供前端提示用户保存前处理。
     */
    private final List<String> saveRiskWarnings;

    /** 请求时底图 graph_json 的稳定哈希前缀，前端用来判断确认期间画布是否被手改 */
    private final String baseGraphHash;

    /** 转成通用校验结果（仅 ok / errors / warnings） */
    public DesignValidationResult toValidationResult() {
        return DesignValidationResult.builder()
                .ok(ok)
                .errors(errors)
                .warnings(warnings)
                .build();
    }
}
