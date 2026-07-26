package com.qualitest.ai.scenario.flow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 设计输入框 @ 引用的单条结构化数据。
 * <p>
 * type 取值：api、node、run、var；subtype 仅 var 使用（flow、env、asset）。
 */
@Getter
@Setter
public class AiDesignMention {

    /** 引用类型：api | node | run | var */
    private String type;

    /** 业务主键或变量键名 */
    private String id;

    /** 用户可见展示名 */
    private String label;

    /** 变量子类型，仅 type=var 时有效 */
    private String subtype;

    /** 环境 id，env 类变量可选 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long envId;
}
