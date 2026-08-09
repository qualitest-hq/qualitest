package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 图节点 data 里常见的 Map/List 松散类型转换（JSONObject / JSONArray / 原生集合）。
 * 设计期门禁共用，避免各 Gate 各写一份 asMap/asList。
 */
final class GraphDataLists {

    private GraphDataLists() {}

    static Map<?, ?> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        if (raw instanceof JSONObject obj) {
            return obj;
        }
        return null;
    }

    static List<?> asList(Object raw) {
        if (raw instanceof List<?> list) {
            return list;
        }
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        return List.of();
    }
}
