package com.qualitest.flow.support;

/**
 * 流程模块单元测试的 Maven 运行命令。
 * <p>
 * 在仓库 {@code qualitest} 目录下执行，将 {@code TestClass} 换为具体测试类名即可：
 * <p>
 * mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestClass
 */
public final class FlowTestRun {

    /** mvn test 命令前缀（末尾拼接测试类简单名） */
    public static final String MVN =
            "mvn test -DskipTests=false -pl qualitest-system -am -Dtest=";

    private FlowTestRun() {
    }

    public static String of(Class<?> testClass) {
        return MVN + testClass.getSimpleName();
    }
}
