package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import com.qualitest.flow.node.impl.DelayNodeHandler;
import com.qualitest.flow.node.impl.EntryNodeHandler;
import com.qualitest.flow.node.impl.HttpNodeHandler;
import com.qualitest.flow.node.impl.InputNodeHandler;
import com.qualitest.flow.node.impl.PluginActionNodeHandler;
import com.qualitest.flow.node.impl.ReturnNodeHandler;
import com.qualitest.flow.node.impl.ScriptNodeHandler;
import com.qualitest.flow.node.impl.SubflowNodeHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点执行器注册表：按节点 type 路由到对应的节点执行器。
 */
@Component
public class NodeHandlerRegistry {

    /** 已注册的 Handler 列表，按 type 匹配第一个 supports 为真的实现 */
    private final List<NodeHandler> handlers;

    /**
     * Spring 构造：注册全部内置节点执行器，包括入口与插件步骤。
     */
    @Autowired
    public NodeHandlerRegistry(HttpNodeHandler httpNodeHandler,
                               AssertNodeHandler assertNodeHandler,
                               DelayNodeHandler delayNodeHandler,
                               ConditionNodeHandler conditionNodeHandler,
                               AssignNodeHandler assignNodeHandler,
                               ScriptNodeHandler scriptNodeHandler,
                               SubflowNodeHandler subflowNodeHandler,
                               InputNodeHandler inputNodeHandler,
                               ReturnNodeHandler returnNodeHandler,
                               EntryNodeHandler entryNodeHandler,
                               PluginActionNodeHandler pluginActionNodeHandler) {
        this.handlers = List.of(
                httpNodeHandler,
                assertNodeHandler,
                delayNodeHandler,
                conditionNodeHandler,
                assignNodeHandler,
                scriptNodeHandler,
                subflowNodeHandler,
                inputNodeHandler,
                returnNodeHandler,
                entryNodeHandler,
                pluginActionNodeHandler
        );
    }

    /**
     * 测试专用：注入自定义 Handler 列表（非 Spring Bean 构造路径）。
     */
    public NodeHandlerRegistry(List<NodeHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    /**
     * 启动断言：平台 FlowNodeType 枚举每一项，以及协议入口抽样，均须能 resolve 到 Handler。
     */
    @PostConstruct
    void assertExecutableHandlersPresent() {
        List<String> missing = new ArrayList<>();
        for (FlowNodeType type : FlowNodeType.values()) {
            String code = type.getCode();
            boolean ok = handlers.stream().anyMatch(h -> h.supports(code));
            if (!ok) {
                missing.add(code);
            }
        }
        // 协议入口走 EntryHandler：抽样 tcp-entry（枚举不逐一列出）
        if (handlers.stream().noneMatch(h -> h.supports("tcp-entry"))) {
            missing.add("tcp-entry(protocol)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "NodeHandler 未覆盖平台节点类型: " + String.join(", ", missing));
        }
    }

    /**
     * 按 type 查找 Handler；未注册时抛 TF_NODE_UNSUPPORTED。
     */
    public NodeHandler requireHandler(String type) {
        if (type == null || type.isBlank()) {
            throw unsupported(type);
        }
        return handlers.stream()
                .filter(handler -> handler.supports(type))
                .findFirst()
                .orElseThrow(() -> unsupported(type));
    }

    /**
     * 执行单步：根据 node.type 路由 Handler 并返回 StepResult。
     */
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        NodeHandler handler = requireHandler(node.getType());
        return handler.execute(ctx, node, incomingEdgeId);
    }

    /** 构造“不支持的节点类型”异常，type 为空时展示为“(空)” */
    private static FlowExecutionException unsupported(String type) {
        String display = type == null || type.isBlank() ? "(空)" : type;
        return new FlowExecutionException(
                FlowErrorCode.TF_NODE_UNSUPPORTED,
                "不支持的节点类型: " + display
        );
    }
}
