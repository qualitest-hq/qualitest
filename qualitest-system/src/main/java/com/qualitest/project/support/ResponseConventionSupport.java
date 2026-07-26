package com.qualitest.project.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 测试项目响应约定（表字段 response_convention）的默认值、解析与规范化。
 * <p>
 * 约定描述被测系统统一响应包装：
 * 业务码字段（codePath）、成功码列表（successValues）、
 * 错误消息字段（messagePath）、业务数据包装字段（dataPath）。
 * 空白或非法 JSON 时回退到默认值：code / [200] / msg / data。
 */
public final class ResponseConventionSupport {

    /** 默认业务码字段名 */
    public static final String DEFAULT_CODE_PATH = "code";
    /** 默认错误消息字段名 */
    public static final String DEFAULT_MESSAGE_PATH = "msg";
    /** 默认业务数据包装字段名 */
    public static final String DEFAULT_DATA_PATH = "data";
    /** 默认成功业务码列表 */
    public static final List<Integer> DEFAULT_SUCCESS_VALUES = List.of(200);

    /**
     * 新建项目写入库的默认约定 JSON。
     * 字段：codePath、successValues、messagePath、dataPath。
     */
    public static final String DEFAULT_JSON =
            "{\"codePath\":\"code\",\"successValues\":[200],\"messagePath\":\"msg\",\"dataPath\":\"data\"}";

    private ResponseConventionSupport() {
    }

    /**
     * 解析约定 JSON；空白或非法时返回默认约定。
     */
    public static Parsed parseOrDefault(String raw) {
        JSONObject obj = parseObject(raw);
        String codePath = firstNonBlank(obj.getString("codePath"), DEFAULT_CODE_PATH);
        String messagePath = firstNonBlank(obj.getString("messagePath"), DEFAULT_MESSAGE_PATH);
        String dataPath = firstNonBlank(obj.getString("dataPath"), DEFAULT_DATA_PATH);
        List<Integer> successValues = readSuccessValues(obj.getJSONArray("successValues"));
        if (successValues.isEmpty()) {
            successValues = List.copyOf(DEFAULT_SUCCESS_VALUES);
        }
        return new Parsed(codePath, messagePath, dataPath, successValues);
    }

    /**
     * 转为结构化 JSON 对象（字段补全后的约定）。
     * 用于项目详情返回、设置页预览、工具输出等场景。
     */
    public static JSONObject toJsonObject(String raw) {
        Parsed parsed = parseOrDefault(raw);
        JSONObject out = new JSONObject(new LinkedHashMap<>());
        out.put("codePath", parsed.codePath());
        out.put("successValues", parsed.successValues());
        out.put("messagePath", parsed.messagePath());
        out.put("dataPath", parsed.dataPath());
        return out;
    }

    /**
     * 规范化用户提交的约定并序列化为 JSON 字符串，用于写库。
     */
    public static String normalizeToJson(String raw) {
        return toJsonObject(raw).toJSONString();
    }

    /** 解析原始 JSON；失败则返回默认对象 */
    private static JSONObject parseObject(String raw) {
        if (StrUtil.isBlank(raw)) {
            return JSON.parseObject(DEFAULT_JSON);
        }
        try {
            JSONObject obj = JSON.parseObject(raw);
            return obj != null ? obj : JSON.parseObject(DEFAULT_JSON);
        } catch (Exception e) {
            return JSON.parseObject(DEFAULT_JSON);
        }
    }

    /** 从 JSON 数组读取成功码，忽略 null */
    private static List<Integer> readSuccessValues(JSONArray arr) {
        List<Integer> out = new ArrayList<>();
        if (arr == null || arr.isEmpty()) {
            return out;
        }
        for (int i = 0; i < arr.size(); i++) {
            Integer v = arr.getInteger(i);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private static String firstNonBlank(String value, String fallback) {
        return StrUtil.isNotBlank(value) ? value.trim() : fallback;
    }

    /**
     * 解析后的响应约定四元组。
     *
     * @param codePath      业务码字段路径
     * @param messagePath   错误消息字段路径
     * @param dataPath      业务数据包装字段路径
     * @param successValues 成功业务码列表
     */
    public record Parsed(String codePath, String messagePath, String dataPath, List<Integer> successValues) {
        public Parsed {
            successValues = successValues != null ? List.copyOf(successValues) : List.of();
        }
    }
}
