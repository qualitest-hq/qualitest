package com.qualitest.flow.migrate;

/**
 * 测试流 graph_json schema 版本常量。
 */
public final class GraphSchemaVersions {

    /** 首版（当前）：meta 完整、condition branches 合法、边仅持久化四字段 */
    public static final int V1 = 1;

    /** 缺失 schemaVersion 时的默认版本（项目未正式上线，无历史 v0） */
    public static final int DEFAULT = V1;

    /** 当前写入与迁移目标版本 */
    public static final int CURRENT = V1;

    private GraphSchemaVersions() {
    }
}
