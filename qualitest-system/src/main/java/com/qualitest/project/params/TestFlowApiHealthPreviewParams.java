package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试流 API 语义预检请求体。
 * <p>
 * 把当前画布序列化后的 graph_json 传给预检接口，只做体检、不写库。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestFlowApiHealthPreviewParams {

    /**
     * 当前画布的 graph_json 全文（JSON 字符串）。
     * 为空时按空图处理，通常不产生 HTTP 节点告警。
     */
    private String graphJson;
}
