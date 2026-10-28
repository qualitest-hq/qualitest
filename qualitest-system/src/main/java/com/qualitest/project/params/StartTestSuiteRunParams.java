package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * 开始一次套件运行参数（须指定已保存的套件）
 *
 * @author qualitest
 * @since 2026-10-28
 */
@Getter
@Setter
public class StartTestSuiteRunParams {

    /** 测试项目ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 套件ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 统一环境覆盖 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /** 非通过即停；空则跟随套件或默认 true */
    private Boolean stopOnNonPass;

    /** 名称快照；空则用套件名 */
    private String suiteNameSnapshot;
}
