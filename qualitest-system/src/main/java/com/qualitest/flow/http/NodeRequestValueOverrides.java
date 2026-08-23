package com.qualitest.flow.http;

import lombok.Data;

import java.util.Map;

/**
 * 流程 HTTP 节点上的请求测值覆盖。
 * 存在节点 data.requestValueOverrides：只存相对接口资产默认值多出来的差分，不进接口资产表。
 */
@Data
public class NodeRequestValueOverrides {
    /** 按参数名覆盖 query / path / form-data / urlencoded 的 value */
    private Map<String, Object> paramDefaults;
    /** 覆盖 JSON body 调试示例 */
    private Object bodyExample;
}
