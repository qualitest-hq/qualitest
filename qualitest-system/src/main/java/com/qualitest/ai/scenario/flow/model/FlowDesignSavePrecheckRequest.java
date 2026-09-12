package com.qualitest.ai.scenario.flow.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存前预检请求体。
 * <p>
 * 前端在点保存前把当前拟持久化的画布 JSON 与项目 id 发来，
 * 服务端检查鉴权凭证来源、登录抽取、HTTP 必填测值是否齐全，不写库。
 */
@Getter
@Setter
public class FlowDesignSavePrecheckRequest {

    /** 测试项目 id，用于加载项目鉴权配置与接口定义 */
    private Long testProjectId;

    /** 拟保存的 graph_json 字符串 */
    private String graphJson;
}
