package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试流入参定义，挂在图 {@code meta.inputSpec}。
 * <p>
 * 描述有哪些入参字段，以及是否把本流开放为 HTTP 入站入口。
 * 开放入站跑完后，HTTP 回包只从运行时 {@code out} 作用域组装，不再从 {@code flow} 取回包键。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphInputSpec {

    /** 入参字段列表；按 name 写入运行时 {@code in} */
    @Builder.Default
    private List<GraphInputField> fields = new ArrayList<>();

    /**
     * 是否开放 HTTP 入站。
     * 为 true 时按 {@link #path} 对外承接请求：握连接同步跑图后把 {@code out} 写回 HTTP 响应。
     */
    private Boolean open;

    /**
     * 开放入站完整相对 path（须以 {@code /} 开头），{@code open=true} 时必填。
     * 例：{@code /v3/refund}；由入站 Filter 按全 path 索引命中后处理。
     */
    private String path;

    /** 握连接最长等待毫秒；空表示由执行侧使用内置默认 */
    private Long holdTimeoutMs;

    /**
     * 开放入站绑质衡侧运行环境 id（字符串雪花）。
     * {@code open=true} 时必填；仿第三方请求不会传环境，开跑只用本字段。
     */
    private String runEnvId;

    /**
     * 旧版入参预设兜底 id；新图以场景 {@code inSeed} 为准，通常不再使用。
     */
    private String defaultPresetId;
}
