package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按项目鉴权刷新本流托管头（预览提案，不写库）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshAuthHeadersParams {

    /** 测试项目 id（必填） */
    private Long testProjectId;

    /** 当前画布 graph_json 全文（JSON 字符串）；用草稿，不强制已保存 */
    private String graphJson;
}
