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
 * 项目模板（表 test_project_template）。
 * <p>
 * 一行模板描述一套可勾选进项目的鉴权与预制资产：
 * 预制接口（必填）、预制参数 flow/env/asset（可选）、预制测试流（可选）、预制 AI 提示词（可选）。
 * 不存托管请求头；勾选进项目时由系统根据预制测试流里的抽取规则生成头与凭证规则。
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

    /** 模板名称；未删除范围内唯一；勾选进项目后用作鉴权 Profile 名称。 */
    private String templateName;

    /**
     * 路径匹配 JSON。
     * 常见形态：{"pathPrefix":["/api/"]}；空表示不做路径前缀匹配。
     */
    private String matchConfig;

    /**
     * 预制接口 JSON 数组（必填，不能为空数组）。
     * 勾选进项目时写入鉴权 Profile，并按 method+path 种子到项目接口表（已存在则跳过）。
     */
    private String templateApis;

    /**
     * 预制参数 JSON 数组（可空，缺省按 []）。
     * 面板主路径为 kind=env（环境变量）与 kind=asset（素材库）；
     * kind=flow 仍兼容：叠进种子流默认场景 flowSeed（仅调试用，勿塞口令）。
     */
    private String templateParams;

    /**
     * 预制测试流 JSON 数组（可空，缺省按 []）。
     * 勾选进项目时按 flowName 种子测试流（同名跳过）；
     * 流内 HTTP 节点的 extracts 用于生成凭证规则与托管头。
     */
    private String templateFlows;

    /**
     * 预制 AI 提示词 JSON 数组（可空，缺省按 []）。
     * 勾选进项目时种子为项目级 ai_prompt_template（同 sessionScene+title 跳过）。
     */
    private String templatePrompts;

    /** 内置标记：0 自定义（可改可删），1 内置（只读，可克隆为自定义）。 */
    private Integer builtinStatus;

    /** 启用标记：0 禁用（不可勾选进项目），1 启用。 */
    private Integer enableStatus;

    /** 列表排序，数值越小越靠前。 */
    private Integer sortNum;

    /** 删除标记：0 正常，1 已删。 */
    private Integer delStatus;
}
