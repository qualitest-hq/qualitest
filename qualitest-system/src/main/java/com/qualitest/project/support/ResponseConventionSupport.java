package com.qualitest.project.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多端响应约定的默认值、解析、规范化和补丁合并。
 * <p>
 * 约定描述被测系统统一响应包装四字段：
 * <ul>
 *   <li>codePath：业务码所在字段路径</li>
 *   <li>successValues：视为成功的业务码列表</li>
 *   <li>messagePath：错误消息字段路径</li>
 *   <li>dataPath：业务数据包装字段路径</li>
 * </ul>
 * 输入空白或非法时回退为 code / [200] / msg / data。
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
     * 缺省约定的 JSON 字符串（四字段齐全）。
     * 新建 Profile、空约定回落时使用。
     */
    public static final String DEFAULT_JSON =
            "{\"codePath\":\"code\",\"successValues\":[200],\"messagePath\":\"msg\",\"dataPath\":\"data\"}";

    private ResponseConventionSupport() {
    }

    /**
     * 解析约定 JSON 为结构化四元组。
     * 空白、非法或字段残缺时用缺省值补齐。
     *
     * @param raw 约定 JSON 字符串，可空
     * @return 补全后的四元组
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
     * 将约定转为 JSON 对象（四字段已补全）。
     * 供接口回执、工具输出等直接序列化场景使用。
     *
     * @param raw 约定 JSON 字符串，可空
     * @return 含四字段的 JSON 对象
     */
    public static JSONObject toJsonObject(String raw) {
        return new JSONObject(toMap(raw));
    }

    /**
     * 将约定 JSON 转为四字段 Map（已补全缺省值）。
     * 供写库、提案快照、Profile 落盘使用。
     *
     * @param raw 约定 JSON 字符串，可空
     * @return 有序四字段 Map
     */
    public static Map<String, Object> toMap(String raw) {
        Parsed parsed = parseOrDefault(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("codePath", parsed.codePath());
        out.put("successValues", parsed.successValues());
        out.put("messagePath", parsed.messagePath());
        out.put("dataPath", parsed.dataPath());
        return out;
    }

    /**
     * 将 Map 形态约定规范为四字段 Map。
     * null 或空 Map 时返回缺省四字段。
     *
     * @param raw 约定 Map，可空
     * @return 有序四字段 Map
     */
    public static Map<String, Object> toMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return toMap((String) null);
        }
        return toMap(JSON.toJSONString(raw));
    }

    /**
     * 规范化约定并序列化为 JSON 字符串，用于持久化。
     *
     * @param raw 原始约定 JSON，可空
     * @return 四字段齐全的 JSON 字符串
     */
    public static String normalizeToJson(String raw) {
        return toJsonObject(raw).toJSONString();
    }

    /**
     * 在已有约定上浅合并补丁，再规范化为 JSON。
     * 补丁可含 codePath、messagePath、dataPath、successValues；
     * 未出现的字段保留已有值（已有也为空时用缺省）。
     *
     * @param existingRaw 当前约定 JSON，可空
     * @param patch       待合并字段，可空（空则只规范化已有值）
     * @return 合并并规范化后的 JSON 字符串
     */
    public static String mergePatchToJson(String existingRaw, Map<String, Object> patch) {
        Parsed base = parseOrDefault(existingRaw);
        JSONObject merged = new JSONObject(new LinkedHashMap<>());
        merged.put("codePath", base.codePath());
        merged.put("successValues", base.successValues());
        merged.put("messagePath", base.messagePath());
        merged.put("dataPath", base.dataPath());
        if (patch != null && !patch.isEmpty()) {
            if (patch.containsKey("codePath") && patch.get("codePath") != null) {
                String v = String.valueOf(patch.get("codePath")).trim();
                if (!v.isEmpty()) {
                    merged.put("codePath", v);
                }
            }
            if (patch.containsKey("messagePath") && patch.get("messagePath") != null) {
                String v = String.valueOf(patch.get("messagePath")).trim();
                if (!v.isEmpty()) {
                    merged.put("messagePath", v);
                }
            }
            if (patch.containsKey("dataPath") && patch.get("dataPath") != null) {
                String v = String.valueOf(patch.get("dataPath")).trim();
                if (!v.isEmpty()) {
                    merged.put("dataPath", v);
                }
            }
            if (patch.containsKey("successValues")) {
                List<Integer> values = coerceSuccessValues(patch.get("successValues"));
                if (!values.isEmpty()) {
                    merged.put("successValues", values);
                }
            }
        }
        return normalizeToJson(merged.toJSONString());
    }

    /** 将补丁中的 successValues 收成整数列表（支持数组、集合、单值）。 */
    private static List<Integer> coerceSuccessValues(Object raw) {
        List<Integer> out = new ArrayList<>();
        if (raw instanceof JSONArray arr) {
            return readSuccessValues(arr);
        }
        if (raw instanceof Collection<?> coll) {
            for (Object item : coll) {
                Integer v = coerceInt(item);
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }
        Integer single = coerceInt(raw);
        if (single != null) {
            out.add(single);
        }
        return out;
    }

    /** 把 Number 或数字字符串转为 Integer；无法解析则返回 null。 */
    private static Integer coerceInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 解析原始 JSON；空白或解析失败时返回缺省约定对象。 */
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

    /** 从 JSON 数组读取成功业务码，跳过 null 元素。 */
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

    /** 取非空白字符串，否则用回落值。 */
    private static String firstNonBlank(String value, String fallback) {
        return StrUtil.isNotBlank(value) ? value.trim() : fallback;
    }

    /**
     * 解析后的响应约定四元组（不可变 successValues）。
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
