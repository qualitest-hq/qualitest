package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 运行场景条目：环境与入参值树。
 * <p>
 * 挂在 {@code scenario-entry.data.scenarios[]}；开跑时 envId 选环境，inSeed 灌入 {@code ctx.in}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphRunScenario {

    /** 场景 id */
    private String id;

    /** 场景显示名称 */
    private String name;

    /**
     * 测试项目环境 id（雪花 ID 以字符串存避免精度丢失）。
     */
    private String envId;

    /**
     * 入参值树；开跑灌入 {@code ctx.in}。
     */
    @Builder.Default
    private Map<String, Object> inSeed = new HashMap<>();

    /** 场景说明（可选） */
    private String remark;

    /**
     * 节点失败时的处理策略。
     * fail：Run 直接失败；prompt：暂停等待人工（预留）。
     */
    private String onNodeFailure;

    /**
     * 打 checkpoint 失败时的处理策略。
     * abort：中止 Run；continue：跳过本次 checkpoint 继续执行节点；prompt：暂停（预留）。
     */
    private String onSnapshotFailure;

    /**
     * 单次运行步数上限；空则用系统默认。
     * 合法范围 1～1000，超出由解析侧裁剪。
     */
    private Integer maxSteps;
}