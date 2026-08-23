package com.qualitest.api.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 接口响应结构：描述各响应条目的 id、状态码、contentType、schema。
 * 不含响应示例正文；示例写在测值配置的 examplesById。
 */
@Data
public class ApiResponseStructure {
    /** 配置版本号 */
    private Integer configVersion;
    /** 响应条目列表 */
    private List<Map<String, Object>> responses;
}
