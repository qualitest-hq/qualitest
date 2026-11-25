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
 * 步数上限与存档失败策略不在场景条目上：见入口 {@code data.maxSteps} 与存档点插件环境 config。
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
}
