package com.qualitest.flow.model;

/**
 * 测试流 graph_json schema 版本常量。
 */
public final class GraphSchemaVersions {

    /** 首版：meta 完整、condition branches 合法、边仅持久化四字段 */
    public static final int V1 = 1;

    /** 缺失 schemaVersion 时视为该版本 */
    public static final int DEFAULT = V1;

    /** 新保存图写入的版本 */
    public static final int CURRENT = V1;

    private GraphSchemaVersions() {
    }
}
