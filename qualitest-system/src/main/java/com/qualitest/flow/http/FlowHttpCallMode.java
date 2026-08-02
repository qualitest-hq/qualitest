package com.qualitest.flow.http;

/**
 * HTTP 节点 {@code data.callMode} 取值。
 * <ul>
 *   <li>{@link #PROJECT} — 调项目已登记接口，必填 {@code testProjectApiId}</li>
 *   <li>{@link #EXTERNAL} — 调项目外 URL，必填 {@code externalUrl} 与 {@code httpMethod}，禁止 {@code testProjectApiId}</li>
 * </ul>
 * 持久化后须为合法 callMode；AI / 设计 Normalizer 在空缺时补 {@link #PROJECT}，
 * 运行引擎本身不做缺省推断。
 */
public final class FlowHttpCallMode {

    public static final String PROJECT = "project";
    public static final String EXTERNAL = "external";

    private FlowHttpCallMode() {
    }

    public static boolean isKnown(String callMode) {
        return PROJECT.equals(callMode) || EXTERNAL.equals(callMode);
    }

    public static boolean isExternal(String callMode) {
        return EXTERNAL.equals(callMode);
    }

    public static boolean isProject(String callMode) {
        return PROJECT.equals(callMode);
    }
}
