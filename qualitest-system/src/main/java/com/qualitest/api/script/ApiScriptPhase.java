package com.qualitest.api.script;

/**
 * API 前置/后置脚本执行阶段。
 */
public enum ApiScriptPhase {

    PRE("prerequest"),
    POST("test");

    private final String eventName;

    ApiScriptPhase(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
