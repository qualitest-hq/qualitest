package com.qualitest.common.test;

/**
 * 单元测试控制台分段输出。
 */
public final class FlowTestSections {

    private FlowTestSections() {
    }

    public static void begin(String name) {
        System.out.println();
        System.out.println("========== " + name + " ==========");
    }

    public static void end(String name) {
        System.out.println("========== " + name + " PASSED ==========");
        System.out.println();
    }

    public static void log(String line) {
        System.out.println("  " + line);
    }

    public static String quote(Object value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
    }
}
