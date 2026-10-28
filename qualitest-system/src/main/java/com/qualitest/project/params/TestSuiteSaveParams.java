package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建/更新测试套件参数（头 + 项整表替换）
 *
 * @author qualitest
 * @since 2026-10-28
 */
@Getter
@Setter
public class TestSuiteSaveParams {

    /** 套件ID（更新时必填） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 测试项目ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 套件名称 */
    private String suiteName;

    /** 套件说明 */
    private String suiteDescription;

    /** 非通过即停；空则默认 true */
    private Boolean stopOnNonPass;

    /** 套件项列表 */
    private List<TestSuiteItemSaveParams> items;
}
