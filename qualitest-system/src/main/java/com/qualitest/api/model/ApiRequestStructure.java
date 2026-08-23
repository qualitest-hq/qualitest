package com.qualitest.api.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 接口请求结构：描述 method、参数定义、声明头、body 模式与 schema。
 * 不含调试用的参数 value 与 body 示例；那些写在测值配置里。
 */
@Data
public class ApiRequestStructure {
    /** 配置版本号 */
    private Integer configVersion;
    /** HTTP 方法，如 GET / POST */
    private String method;
    /** Query 参数定义列表 */
    private List<Map<String, Object>> queryParams;
    /** Path 参数定义列表 */
    private List<Map<String, Object>> pathParams;
    /** 声明式请求头参数定义（契约，不是实际发出的 KV） */
    private List<Map<String, Object>> declaredHeaders;
    /** 请求体：mode、json.schema、formData / urlencoded 等 */
    private Map<String, Object> body;
}
