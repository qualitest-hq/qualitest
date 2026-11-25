package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * 套件保存时的单项参数
 *
 * @author qualitest
 * @since 2026-10-28
 */
@Getter
@Setter
public class TestSuiteItemSaveParams {

    /** 测试流ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 入口节点 id（多场景入口时指定） */
    private String entryNodeId;

    /** 场景ID（空表示默认场景） */
    private String runScenarioId;

    /** 排序 */
    private Integer sortNum;
}
