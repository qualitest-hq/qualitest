package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;

/**
 * 接口期望响应形态的读写与对照。
 * <p>
 * 字段写在接口 responseConfig 顶层 {@code expectedResponseKind}，取值 json / xml / html / any，缺省 json。
 * 跑流时用实际响应形态判断是否符合期望；不符时期望匹配标志为 false，步骤本身不因此失败，
 * 供后续条件节点按形态分支（例如未登录返回 HTML 登录页时走登录）。
 */
public final class ExpectedResponseKindSupport {

    /** responseConfig 顶层字段名 */
    public static final String FIELD = "expectedResponseKind";
    /** 期望：JSON 业务体 */
    public static final String KIND_JSON = "json";
    /** 期望：XML 业务体 */
    public static final String KIND_XML = "xml";
    /** 期望：HTML 等非结构化业务体（实际形态须为 nonJson） */
    public static final String KIND_HTML = "html";
    /** 期望：不限制形态 */
    public static final String KIND_ANY = "any";

    /** 实际形态：可解析为 JSON 对象或数组 */
    public static final String ACTUAL_JSON = "json";
    /** 实际形态：按 XML 解析成功 */
    public static final String ACTUAL_XML = "xml";
    /** 实际形态：非 JSON（含 HTML、纯文本、空体、解析失败的 XML） */
    public static final String ACTUAL_NON_JSON = "nonJson";

    private ExpectedResponseKindSupport() {
    }

    /**
     * 规范期望取值。
     * 空白或无法识别时返回 json。
     *
     * @param raw 原始期望字符串
     * @return json / xml / html / any 之一
     */
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) {
            return KIND_JSON;
        }
        String x = raw.trim().toLowerCase(Locale.ROOT);
        if (KIND_HTML.equals(x) || KIND_ANY.equals(x) || KIND_JSON.equals(x) || KIND_XML.equals(x)) {
            return x;
        }
        return KIND_JSON;
    }

    /**
     * 从接口 responseConfig JSON 字符串读取期望形态。
     * 配置空白、非法 JSON 或无该字段时返回 json。
     *
     * @param responseConfigJson 接口响应配置全文
     * @return 规范化后的期望形态
     */
    public static String fromResponseConfigJson(String responseConfigJson) {
        if (StrUtil.isBlank(responseConfigJson)) {
            return KIND_JSON;
        }
        try {
            Object parsed = JSON.parse(responseConfigJson);
            if (parsed instanceof JSONObject obj) {
                return normalize(obj.getString(FIELD));
            }
        } catch (Exception ignored) {
            // 非法 JSON：按默认 json
        }
        return KIND_JSON;
    }

    /**
     * 从响应配置对象节点读取期望形态。
     * 无字段或非文本时返回 json。
     *
     * @param root responseConfig 根对象
     * @return 规范化后的期望形态
     */
    public static String fromObjectNode(ObjectNode root) {
        if (root == null || !root.has(FIELD) || root.get(FIELD).isNull()) {
            return KIND_JSON;
        }
        JsonNode n = root.get(FIELD);
        if (n != null && n.isTextual()) {
            return normalize(n.asText());
        }
        return KIND_JSON;
    }

    /**
     * 把期望形态写入响应配置根对象顶层（写入前会规范化）。
     *
     * @param root 响应配置根对象
     * @param kind 期望形态原文
     */
    public static void putOnObjectNode(ObjectNode root, String kind) {
        if (root == null) {
            return;
        }
        root.put(FIELD, normalize(kind));
    }

    /**
     * 仅按已解析 body 的数据结构判定形态：Map / JSON 对象 / 列表 / JSON 数组 → json，其余 → nonJson。
     * 不识别 xml：XML 若已转成 Map，调用本方法会得到 json；XML 形态须由正文解析阶段单独给出。
     *
     * @param parsedBody 解析后的响应体
     * @return json 或 nonJson
     */
    public static String detectActualKind(Object parsedBody) {
        if (parsedBody == null) {
            return ACTUAL_NON_JSON;
        }
        if (parsedBody instanceof Map<?, ?> || parsedBody instanceof JSONObject) {
            return ACTUAL_JSON;
        }
        if (parsedBody instanceof java.util.List<?> || parsedBody instanceof JSONArray) {
            return ACTUAL_JSON;
        }
        return ACTUAL_NON_JSON;
    }

    /**
     * 判断实际形态是否满足期望。
     * any 恒为通过；html 要求实际为 nonJson；xml 要求实际为 xml；其余（含 json）要求实际为 json。
     *
     * @param expectedKind 期望形态
     * @param actualKind   实际形态
     * @return 是否匹配
     */
    public static boolean matches(String expectedKind, String actualKind) {
        String expected = normalize(expectedKind);
        if (KIND_ANY.equals(expected)) {
            return true;
        }
        String actual = actualKind == null ? ACTUAL_NON_JSON : actualKind;
        if (KIND_HTML.equals(expected)) {
            return ACTUAL_NON_JSON.equals(actual);
        }
        if (KIND_XML.equals(expected)) {
            return ACTUAL_XML.equals(actual);
        }
        return ACTUAL_JSON.equals(actual);
    }

    /**
     * 按接口响应配置判断实际 body 是否符合期望。
     * 先按数据结构判定形态再对照；配置空白时视为匹配。
     *
     * @param responseConfigJson 接口响应配置；可空
     * @param parsedBody         解析后的响应体
     * @return 是否匹配或配置空白应跳过
     */
    public static boolean matchesOrSkip(String responseConfigJson, Object parsedBody) {
        return matchesOrSkipByKind(responseConfigJson, detectActualKind(parsedBody));
    }

    /**
     * 按接口响应配置判断给定实际形态是否符合期望。
     * 配置空白时视为匹配（无绑定接口或未配期望时不误判）。
     *
     * @param responseConfigJson 接口响应配置；可空
     * @param actualKind         实际形态：json / xml / nonJson
     * @return 是否匹配或配置空白应跳过
     */
    public static boolean matchesOrSkipByKind(String responseConfigJson, String actualKind) {
        if (StrUtil.isBlank(responseConfigJson)) {
            return true;
        }
        String expected = fromResponseConfigJson(responseConfigJson);
        return matches(expected, actualKind);
    }
}
