package com.qualitest.flow.http;

/**
 * 测试流 project 模式 HTTP 节点的路径字段处理。
 * <p>
 * 发出请求时的 URL 路径只取所绑 API 资产上的 apiPath。
 * 节点 data 里不保存 apiPath；若残留该字段则删除。
 */
public final class FlowHttpNodePathSupport {

    private FlowHttpNodePathSupport() {
    }

    /**
     * 从节点 data（Map）删除 apiPath。
     */
    public static void stripNodeApiPath(java.util.Map<String, Object> data) {
        if (data != null) {
            data.remove("apiPath");
        }
    }

    /**
     * 从节点 data（JSONObject）删除 apiPath。
     */
    public static void stripNodeApiPath(com.alibaba.fastjson2.JSONObject data) {
        if (data != null) {
            data.remove("apiPath");
        }
    }
}
