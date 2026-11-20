package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 入参字段声明。
 * <p>
 * 开放入站解析请求体、以及入参预设灌值时，按 {@link #name} 写入运行时 {@code in} 作用域。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphInputField {

    /** 字段名，对应 {@code in.<name>} */
    private String name;

    /** 画布上展示的名称 */
    private String label;

    /**
     * 字段类型，常用值：{@code string}、{@code number}、{@code boolean}、{@code json}。
     * 开放入站注入时用于识别取值形态；缺省按字符串处理。
     */
    private String type;

    /**
     * 为 true 时表示必填：开放入站缺值会拒绝请求；场景预设填值处会标 * 提示补齐。
     */
    private Boolean required;

    /** 预设与请求均未提供该字段时写入 {@code in} 的缺省值 */
    private Object defaultValue;

    /** 字段说明文案 */
    private String remark;
}
