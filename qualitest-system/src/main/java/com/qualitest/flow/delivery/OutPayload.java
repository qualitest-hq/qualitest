package com.qualitest.flow.delivery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次交付用的定稿出参。
 * <p>
 * 三字段对应运行时 {@code out} Map 的键：
 * <ul>
 *   <li>{@code status} — HTTP 状态码；开放写出缺省时由交付器补 200</li>
 *   <li>{@code headers} — 响应头；键值均可序列化</li>
 *   <li>{@code body} — 响应体，任意 JSON 可表示对象</li>
 * </ul>
 * {@code http-return} / {@code subflow-return} 从当前 {@code ctx.out} 拷贝后立刻交付；
 * 收束兜底则从入口节点 {@code fallbackOut} 或平台默认包构造。
 */
public final class OutPayload {

    /** HTTP 状态码；可为 null，写出前再补默认 */
    private Integer status;
    /** 响应头；始终非 null */
    private Map<String, Object> headers = new LinkedHashMap<>();
    /** 响应体 */
    private Object body;

    public OutPayload() {
    }

    public OutPayload(Integer status, Map<String, Object> headers, Object body) {
        this.status = status;
        this.headers = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>();
        this.body = body;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>();
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    /**
     * 从运行时 out Map、入口 fallbackOut 或同类结构拷贝一份。
     * status 支持 Number 或可解析整数字符串；解析失败保留 null。
     * headers 仅接受 Map；body 有键则原样拷贝（含显式 null）。
     */
    @SuppressWarnings("unchecked")
    public static OutPayload fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new OutPayload();
        }
        OutPayload p = new OutPayload();
        Object status = map.get("status");
        if (status instanceof Number n) {
            p.setStatus(n.intValue());
        } else if (status != null && !String.valueOf(status).isBlank()) {
            try {
                p.setStatus(Integer.parseInt(String.valueOf(status).trim()));
            } catch (NumberFormatException ignored) {
                // 保持 null，写出路径再补 200
            }
        }
        Object headers = map.get("headers");
        if (headers instanceof Map<?, ?> hm) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : hm.entrySet()) {
                if (e.getKey() != null) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            p.setHeaders(copy);
        }
        if (map.containsKey("body")) {
            p.setBody(map.get("body"));
        }
        return p;
    }

    /**
     * 转为可挂到上下文 out / 父图 nodeOuts 的 Map。
     * status 为 null 时不写该键；headers 始终写出（可为空 Map）；body 始终写出。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (status != null) {
            map.put("status", status);
        }
        map.put("headers", headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>());
        map.put("body", body);
        return map;
    }

    /** 独立拷贝，避免交付后调用方改原对象影响已写出内容。 */
    public OutPayload copy() {
        return fromMap(toMap());
    }
}
