package com.qualitest.flow.support;

import com.qualitest.flow.model.GraphNodePosition;

/**
 * flow 模块单元测试的控制台分段输出。
 * <p>
 * begin/end/log/quote 委托 {@link com.qualitest.common.test.FlowTestSections}；
 * formatPosition 为 flow 测试专用扩展。
 */
public final class FlowTestSections {

    private FlowTestSections() {
    }

    public static void begin(String name) {
        com.qualitest.common.test.FlowTestSections.begin(name);
    }

    public static void end(String name) {
        com.qualitest.common.test.FlowTestSections.end(name);
    }

    public static void log(String line) {
        com.qualitest.common.test.FlowTestSections.log(line);
    }

    public static String quote(Object value) {
        return com.qualitest.common.test.FlowTestSections.quote(value);
    }

    public static String formatPosition(GraphNodePosition position) {
        if (position == null) {
            return "null";
        }
        return "x=" + position.getX() + ", y=" + position.getY();
    }
}
