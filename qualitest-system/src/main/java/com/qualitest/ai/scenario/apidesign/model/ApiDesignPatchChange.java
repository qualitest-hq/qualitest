package com.qualitest.ai.scenario.apidesign.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 提交的单条设计变更。
 * <p>
 * 前端按 unitId 勾选后合并进调试/设计工作台草稿，不会自动落库。
 */
@Getter
@Setter
public class ApiDesignPatchChange {

    /**
     * 变更单元稳定 id，供侧栏勾选与同轮去重。
     * 常见取值：script:pre、script:post、
     * constraint:request.queryParams.mobile、
     * testValue:request.paramDefaults.mobile、
     * meta:apiDescription。
     */
    private String unitId;

    /**
     * 变更落点类别：
     * <ul>
     *   <li>script — 前置/后置脚本</li>
     *   <li>request.queryParams / pathParams / declaredHeaders /
     *       body.formData / body.urlencoded — 扁平参数约束</li>
     *   <li>request.body.schema / response.schema — JSON Schema 叶节点约束</li>
     *   <li>testValue.request.paramDefaults / bodyExample /
     *       response.examplesById — 测值</li>
     *   <li>meta — 接口名称或说明</li>
     * </ul>
     */
    private String target;

    /** 仅 script：pre=前置脚本，post=后置脚本 */
    private String phase;

    /**
     * 动作类型：
     * update=整段替换（脚本或说明文案），
     * clear=清空，
     * set=写入测值，
     * updateConstraints=合并结构约束键。
     */
    private String action;

    /**
     * 字段定位：参数名、schema 点分路径（如 user.mobile）、
     * 或测值里 examplesById 的响应条目 id。
     */
    private String path;

    /** 字段当前类型（string/integer 等）；约束校验与 prune 时使用 */
    private String type;

    /** 响应 schema / 响应测值所属条目 id；缺省时前端可回落到第一条响应 */
    private String responseId;

    /** updateConstraints 时要写入的约束键值（pattern、minLength、minValue 等） */
    private Map<String, Object> constraints = new LinkedHashMap<>();

    /** 可与约束一并更新的字段说明文案 */
    private String description;

    /** set 测值时的值，可为字符串或任意 JSON 兼容对象 */
    private Object value;

    /** update 时的完整脚本源码，或 meta 的名称/说明正文 */
    private String content;
}
