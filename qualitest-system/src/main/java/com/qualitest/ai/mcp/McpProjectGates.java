package com.qualitest.ai.mcp;

/**
 * MCP 能力快照：由项目成员角色权限映射为四类 MCP 能力标志。
 *
 * @param readEnabled 具备 mcp:read 时可调用只读 MCP 工具
 * @param autoWriteEnabled 具备 mcp:flow:write 时可调用写流类工具
 * @param autorunEnabled 具备 mcp:run 时可触发跑流工具
 * @param importApisEnabled 具备 mcp:api:import 时可调用接口导入工具
 */
public record McpProjectGates(boolean readEnabled, boolean autoWriteEnabled, boolean autorunEnabled,
                              boolean importApisEnabled) {

    /** 四类 MCP 权限均未开启 */
    public static final McpProjectGates NONE = new McpProjectGates(false, false, false, false);

    /** 仅开启只读 MCP */
    public static final McpProjectGates READ_ONLY = new McpProjectGates(true, false, false, false);
}
