package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 单次运行的场景：指定环境与 flow 变量初值，同一张图可配置多套场景。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphRunScenario {

    /**
     * 场景 id
     */
    private String id;

    /**
     * 场景显示名称
     */
    private String name;

    /**
     * 测试项目环境 id（雪花 ID，JSON 中以字符串存储避免精度丢失）
     */
    private String testProjectEnvId;

    /**
     * flow 变量初值 KV；Run 启动时注入 {@code ctx.flow}
     */
    @Builder.Default
    private Map<String, Object> flowSeed = new HashMap<>();

    /**
     * 场景说明（可选）
     */
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
}
