package com.qualitest.flow.script;

import com.qualitest.flow.context.FlowRunContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.Set;

/**
 * Script 节点 {@code ctx.session} 的 GraalVM 代理。
 * <p>
 * {@code session.get(key)} / {@code session.set(key, value)} 读写 {@link FlowRunContext#getSession()}，
 * 为 Run 级通用 KV（与 HTTP Cookie 鉴权无关；Cookie 走 extracts + 项目 Profile）。
 */
public final class ScriptSessionHost implements ProxyObject {

    private static final Set<String> MEMBERS = Set.of("get", "set");

    private final FlowRunContext runContext;

    public ScriptSessionHost(FlowRunContext runContext) {
        this.runContext = runContext;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "get" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "session.get");
                String name = args[0].asString();
                Map<String, Object> store = runContext.getSession();
                return ScriptValueConverter.toScriptValue(store.get(name));
            };
            case "set" -> (ProxyExecutable) args -> {
                requireArgs(args, 2, "session.set");
                String name = args[0].asString();
                Object guestValue = args[1].isHostObject() ? args[1].asHostObject() : guestToJava(args[1]);
                Object converted = ScriptValueConverter.fromGuest(guestValue);
                runContext.getSession().put(name, converted);
                return converted;
            };
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return MEMBERS.toArray(new String[0]);
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("session 不允许动态添加属性: " + key);
    }

    private static void requireArgs(Value[] args, int min, String name) {
        if (args == null || args.length < min) {
            throw new IllegalArgumentException(name + " 参数不足");
        }
    }

    private static Object guestToJava(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        return value.toString();
    }
}
