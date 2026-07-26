package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphJson;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Web 端「单 Staging 单元确认」的响应体。
 * <p>
 * 确认成功时返回合并该单元后的完整 graph_json，供前端落盘；
 * 失败时 {@code ok=false} 且 {@code graphJson} 为 null，前端不得写入画布。
 */
@Getter
@Builder
public class FlowDesignPatchConfirmResult {

    /** true 表示 errors 为空，允许将该单元写入正式图层 */
    private final boolean ok;

    /** 阻断确认的问题，含依赖未满足与图结构校验错误 */
    private final List<String> errors;

    /** 可确认但建议用户留意的问题，不阻断 confirm */
    private final List<String> warnings;

    /**
     * 确认该单元后的完整 graph_json。
     * 依赖未满足或校验失败时为 null。
     */
    private final GraphJson graphJson;

    /** 依赖补全提示，如「确认 addEdge 需先确认 addNode:xxx」 */
    private final List<String> dependencyHints;

    /** 请求时 base graph_json 的稳定哈希前缀，供前端 confirm 前校验画布是否被手改 */
    private final String baseGraphHash;

    public DesignValidationResult toValidationResult() {
        return DesignValidationResult.builder()
                .ok(ok)
                .errors(errors)
                .warnings(warnings)
                .build();
    }
}
