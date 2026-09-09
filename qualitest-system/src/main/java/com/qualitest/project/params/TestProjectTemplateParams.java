package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 项目模板 Params 对象
 *
 * @author qualitest
 * @since 2026-09-09
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectTemplateParams")
public class TestProjectTemplateParams extends BaseEntity implements Serializable {

    /** 名称模糊匹配。 */
    private String templateName;

    /** 是否内置：0 自定义，1 内置。 */
    private Integer builtinStatus;

    /** 是否启用：0 禁用，1 启用。 */
    private Integer enableStatus;
}
