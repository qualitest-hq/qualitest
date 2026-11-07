package com.qualitest.project.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目运行中心总览回执。
 * <p>
 * {@link #active} 来自缓存（排队中 / 执行中，可取消或从列表移除）；
 * {@link #recent} 来自库表正式 Run（只读历史，点开走既有报告详情）。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectRunHubResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目当前跑流并发模式码。
     * {@code parallel} 并行；{@code serial_by_env} 按环境串行。
     */
    private String runConcurrencyMode;

    /** 排队中 / 执行中（缓存活动项） */
    @Builder.Default
    private List<ProjectRunHubItemResult> active = new ArrayList<>();

    /** 最近正式单流 Run 记录（查库） */
    @Builder.Default
    private List<ProjectRunHubItemResult> recent = new ArrayList<>();
}
