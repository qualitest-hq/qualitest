package com.qualitest.flow.inspect;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目流检查严重性覆盖的 JSON 读写。
 * 落库形态为对象：键是诊断码（核心短码或 {@code 插件id/短码}），值是 {@code ERROR}/{@code WARNING}/{@code OFF}。
 */
public final class InspectionProfileSupport {

    private InspectionProfileSupport() {}

    /**
     * 解析覆盖表。
     * 空串或空白返回空 map；JSON 损坏时也返回空 map，避免一次坏配置拖垮整次校验。
     * 无法识别的严重性字符串会被跳过。
     */
    public static Map<String, InspectionConfiguredSeverity> parseOverrides(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null || obj.isEmpty()) {
                return Map.of();
            }
            Map<String, InspectionConfiguredSeverity> out = new LinkedHashMap<>();
            for (String key : obj.keySet()) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                InspectionConfiguredSeverity sev =
                        InspectionConfiguredSeverity.fromCode(obj.getString(key));
                if (sev != null) {
                    out.put(key.trim(), sev);
                }
            }
            return Collections.unmodifiableMap(out);
        } catch (JSONException e) {
            return Map.of();
        }
    }

    /**
     * 序列化覆盖表。
     * 调用方须传入非 null 的 map；空 map 写出 {@code {}}，表示恢复目录默认。
     */
    public static String toJson(Map<String, InspectionConfiguredSeverity> overrides) {
        if (overrides.isEmpty()) {
            return "{}";
        }
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, InspectionConfiguredSeverity> e : overrides.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            obj.put(e.getKey(), e.getValue().name());
        }
        return obj.toJSONString();
    }
}
