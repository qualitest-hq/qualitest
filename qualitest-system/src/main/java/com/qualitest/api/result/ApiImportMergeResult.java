package com.qualitest.api.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次结构合并完成后的落库内容。
 * <p>
 * 含合并后的 request_config、response_config、test_value_config，
 * 以及仅给单元测试断言用的合并摘要（不写入导入接口返回体）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiImportMergeResult {

    /**
     * 合并后的请求结构 JSON。
     * 参数定义在结构层，参数默认值已迁到 test_value_config.request.paramDefaults。
     */
    private String requestConfig;

    /**
     * 合并后的响应结构 JSON。
     * 用户 example 可能已按响应 id 回填到 responses[].example。
     */
    private String responseConfig;

    /**
     * 合并后的测试值 JSON。
     * 含参数默认值、body 示例、响应 examplesById、已删参数 removedParams 等。
     */
    private String testValueConfig;

    /**
     * 合并过程摘要：参数增删、保留了哪些值项等。
     * 仅进程内/单测使用，不返回给导入调用方。
     */
    private ApiImportMergeSummary mergeSummary;
}
