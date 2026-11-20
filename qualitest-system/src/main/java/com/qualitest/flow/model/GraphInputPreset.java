package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 旧版入参预设：一组具体入参取值，挂在图 {@code meta.inputPresets}。
 * <p>
 * 新图请把取值写在场景 {@code inSeed}。本类型仅兼容未重存的旧图：
 * 当场景尚无 {@code inSeed} 字段时，开跑仍可把 {@link #values} 注入运行时 {@code in}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphInputPreset {

    /** 预设唯一 id */
    private String id;

    /** 画布列表里显示的名称 */
    private String name;

    /**
     * 本预设绑定的项目环境 id（字符串雪花）。
     * 可空；空时仍用场景上的环境或触发参数里的环境。
     */
    private String testProjectEnvId;

    /** 入参键值；Run 启动时整表写入 {@code ctx.in} */
    @Builder.Default
    private Map<String, Object> values = new HashMap<>();

    /** 预设说明 */
    private String remark;

    /**
     * 节点失败处理：{@code fail} 直接失败；{@code prompt} 暂停等人。
     * 有值时覆盖场景上的同名字段。
     */
    private String onNodeFailure;

    /**
     * 打 checkpoint 失败时的处理：{@code abort} / {@code continue} / {@code prompt}。
     * 有值时覆盖场景上的同名字段。
     */
    private String onSnapshotFailure;

    /** 单次运行步数上限；有值时覆盖场景配置；空则用系统默认 */
    private Integer maxSteps;
}
