package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphJson;
import lombok.Builder;
import lombok.Getter;

/**
 * 单条 Staging 单元确认接口的响应体。
 * <p>
 * 请求合法时始终带合并后的 {@link #graphJson}；图结构 / 依赖 / 断言不挡确认。
 * 鉴权/必填由画布 {@code validate?stage=canvas} 图 error 刷新，不进本响应。
 * 非法请求由服务抛业务异常，不走本结构。
 */
@Getter
@Builder
public class FlowDesignPatchConfirmResult {

    /** 确认该单元后的完整 graph_json（请求合法时非 null） */
    private final GraphJson graphJson;

    /** 请求时底图 graph_json 的稳定哈希前缀，前端用来判断确认期间画布是否被手改 */
    private final String baseGraphHash;
}
