package com.qualitest.ai.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具名到实现实例的注册表。
 * <p>
 * 每个 Agent 运行可创建独立实例，避免并发下工具状态互相污染。
 */
public class QualitestToolRegistry {

    private final Map<String, QualitestTool> tools = new HashMap<>();

    public void register(QualitestTool tool) {
        tools.put(tool.getName(), tool);
    }

    public QualitestTool get(String name) {
        return tools.get(name);
    }

    public Map<String, QualitestTool> getAll() {
        return tools;
    }
}
