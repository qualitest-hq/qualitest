package com.qualitest.flow.model;

/**
 * 测试流 graph_json schema 版本常量。
 */
public final class GraphSchemaVersions {

    /** 首版：meta 完整、condition branches 合法、边仅持久化四字段 */
    public static final int V1 = 1;

    /**
     * 第二版：支持入口节点、返回节点与 out 交付、多出边隐式扇出、pluginAction 插件步骤；
     * 新保存的图直接按此版本写入，不会自动升级旧版本图。
     */
    public static final int V2 = 2;

    /** 新保存图写入的版本 */
    public static final int CURRENT = V2;

    /** 工具类，禁止实例化 */
    private GraphSchemaVersions() {
    }
}
