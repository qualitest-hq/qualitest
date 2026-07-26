package com.qualitest.flow.run;

/**
 * test_flow_run.status 字段取值常量。
 */
public final class RunStatus {

    /** 执行中 */
    public static final String RUNNING = "running";
    /** 全部步骤通过 */
    public static final String PASSED = "passed";
    /** 步骤失败且未暂停 */
    public static final String FAILED = "failed";
    /** 等待用户 resume */
    public static final String PAUSED = "paused";
    /** 用户中止或超时中止 */
    public static final String ABORTED = "aborted";

    private RunStatus() {
    }
}
