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

/**
 * 项目模板表 test_project_template。
 * 一行模板对应一套 Profile：头模板 + 预制接口。勾选后拷贝进项目 auth_config。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectTemplate")
public class TestProjectTemplate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板主键。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectTemplateId;

    /** 模板名称，未删除范围内唯一。拷贝到项目后作为 Profile 名。 */
    private String templateName;

    /** 鉴权头名称，如 Authorization、Cookie。 */
    private String headerName;

    /** 鉴权头值模板，如 Bearer {{flow.token}}。 */
    private String headerValueTemplate;

    /** 路径匹配 JSON，如 {"pathPrefix":["/api/"]}；空表示不按前缀切分。 */
    private String matchConfig;

    /** 预制接口 JSON 数组。 */
    private String apis;

    /** 是否内置：0 自定义可改可删，1 内置只读可克隆。 */
    private Integer builtinStatus;

    /** 是否启用：0 禁用，1 启用。禁用的不能勾选到项目。 */
    private Integer enableStatus;

    /** 列表排序，越小越靠前。 */
    private Integer sortNum;

    /** 删除标记：0 正常，1 已删。 */
    private Integer delStatus;
}
