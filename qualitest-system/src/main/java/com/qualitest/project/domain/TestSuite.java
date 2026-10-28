package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.util.Date;

/**
 * 测试套件对象 test_suite
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestSuite")
public class TestSuite extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 套件ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 测试项目ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 套件名称 */
    private String suiteName;

    /** 套件说明 */
    private String suiteDescription;

    /** 非通过即停（1是 0否） */
    private Integer stopOnNonPass;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
