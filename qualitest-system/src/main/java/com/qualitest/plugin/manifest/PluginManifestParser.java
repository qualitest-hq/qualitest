package com.qualitest.plugin.manifest;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析插件说明书文本（JSONC：允许行注释与块注释，再按 JSON 解析）。
 * 校验必填字段、能力名、挂点动作归属、runtime 形态；失败抛 MANIFEST_INVALID。
 */
@Component
public class PluginManifestParser {

    /** 插件 id：小写字母开头，可含数字、点、连字符 */
    private static final Pattern PLUGIN_ID = Pattern.compile("^[a-z][a-z0-9.-]*$");
    /** 能力名：同上规则 */
    private static final Pattern CAP_NAME = Pattern.compile("^[a-z][a-z0-9.-]*$");

    /**
     * 解析说明书全文为内存模型。
     * 先去掉注释再解析 JSON；有能力列表时要求 runtime 合法。
     */
    public PluginManifest parse(String jsonc) {
        if (jsonc == null || jsonc.isBlank()) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "说明书为空");
        }
        String json = stripJsonc(jsonc);
        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (Exception e) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "说明书 JSON 解析失败", e);
        }
        if (root == null) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "说明书为空对象");
        }
        String id = root.getString("id");
        String version = root.getString("version");
        String displayName = root.getString("displayName");
        if (id == null || !PLUGIN_ID.matcher(id).matches()) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "id 非法");
        }
        if (version == null || version.isBlank()) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "version 必填");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "displayName 必填");
        }

        List<PluginManifest.Capability> capabilities = parseCapabilities(root.getJSONArray("capabilities"));
        List<PluginManifest.Hook> hooks = parseHooks(root.getJSONArray("hooks"), capabilities);
        PluginManifest.RuntimeSpec runtime = parseRuntime(root.getJSONObject("runtime"), !capabilities.isEmpty());

        Map<String, Object> configSchema = null;
        if (root.get("configSchema") instanceof JSONObject schemaObj) {
            configSchema = schemaObj.to(Map.class);
        }
        Map<String, Object> ui = null;
        if (root.get("ui") instanceof JSONObject uiObj) {
            ui = uiObj.to(Map.class);
        }

        List<String> isolation = readStringList(root.getJSONArray("isolation"));
        List<String> requires = normalizePluginRefs(readStringList(root.getJSONArray("requires")));
        PluginManifest.ProjectMountSpec projectMount = parseProjectMount(root.getJSONObject("projectMount"));
        List<PluginManifest.PushProduct> pushProducts = parsePushProducts(root.getJSONObject("push"));
        boolean channelProvider = Boolean.TRUE.equals(root.getBoolean("channelProvider"));

        return PluginManifest.builder()
                .id(id)
                .version(version)
                .displayName(displayName)
                .remarks(root.getString("remarks"))
                .capabilities(capabilities)
                .requires(requires)
                .isolation(isolation)
                .hooks(hooks)
                .projectMount(projectMount)
                .configSchema(configSchema)
                .pushProducts(pushProducts)
                .channelProvider(channelProvider)
                .ui(ui)
                .runtime(runtime)
                .rawJson(JSON.toJSONString(root))
                .build();
    }

    /** 解析 capabilities 数组；能力名须匹配命名规则。 */
    private List<PluginManifest.Capability> parseCapabilities(JSONArray arr) {
        List<PluginManifest.Capability> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String name = o.getString("name");
            if (name == null || !CAP_NAME.matcher(name).matches()) {
                throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "capability.name 非法: " + name);
            }
            list.add(PluginManifest.Capability.builder()
                    .name(name)
                    .effect(o.getString("effect"))
                    .gate(o.getString("gate"))
                    .build());
        }
        return list;
    }

    /**
     * 解析 hooks；每个 action 必须是本包已声明的能力名，否则判非法。
     * order 缺省为 100。
     */
    private List<PluginManifest.Hook> parseHooks(JSONArray arr, List<PluginManifest.Capability> capabilities) {
        List<PluginManifest.Hook> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String name = o.getString("name");
            int order = o.getIntValue("order", 100);
            List<String> actions = new ArrayList<>();
            JSONArray actionArr = o.getJSONArray("actions");
            if (actionArr != null) {
                for (int j = 0; j < actionArr.size(); j++) {
                    String action = actionArr.getString(j);
                    boolean owned = capabilities.stream().anyMatch(c -> c.getName().equals(action));
                    if (!owned) {
                        throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "hook action 不属于本包: " + action);
                    }
                    actions.add(action);
                }
            }
            list.add(PluginManifest.Hook.builder().name(name).order(order).actions(actions).build());
        }
        return list;
    }

    private PluginManifest.ProjectMountSpec parseProjectMount(JSONObject obj) {
        if (obj == null || obj.isEmpty()) {
            return null;
        }
        List<PluginManifest.MountField> fields = new ArrayList<>();
        JSONArray fieldArr = obj.getJSONArray("fields");
        if (fieldArr != null) {
            for (int i = 0; i < fieldArr.size(); i++) {
                JSONObject f = fieldArr.getJSONObject(i);
                if (f == null || f.getString("name") == null || f.getString("name").isBlank()) {
                    continue;
                }
                fields.add(PluginManifest.MountField.builder()
                        .name(f.getString("name").trim())
                        .required(Boolean.TRUE.equals(f.getBoolean("required")))
                        .pattern(f.getString("pattern"))
                        .build());
            }
        }
        List<PluginManifest.MountInject> inject = new ArrayList<>();
        JSONArray injectArr = obj.getJSONArray("inject");
        if (injectArr != null) {
            for (int i = 0; i < injectArr.size(); i++) {
                JSONObject inj = injectArr.getJSONObject(i);
                if (inj == null) {
                    continue;
                }
                String from = inj.getString("from");
                String to = inj.getString("to");
                if (from == null || from.isBlank() || to == null || to.isBlank()) {
                    continue;
                }
                inject.add(PluginManifest.MountInject.builder().from(from.trim()).to(to.trim()).build());
            }
        }
        String inheritFrom = obj.getString("inheritFrom");
        if (inheritFrom != null) {
            inheritFrom = stripPluginPrefix(inheritFrom.trim());
            if (inheritFrom.isBlank()) {
                inheritFrom = null;
            }
        }
        if (fields.isEmpty() && inject.isEmpty() && inheritFrom == null) {
            return null;
        }
        return PluginManifest.ProjectMountSpec.builder()
                .fields(fields)
                .inject(inject)
                .inheritFrom(inheritFrom)
                .build();
    }

    private List<PluginManifest.PushProduct> parsePushProducts(JSONObject push) {
        if (push == null) {
            return Collections.emptyList();
        }
        JSONArray arr = push.getJSONArray("products");
        if (arr == null) {
            return Collections.emptyList();
        }
        List<PluginManifest.PushProduct> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null || o.getString("productId") == null || o.getString("productId").isBlank()) {
                continue;
            }
            list.add(PluginManifest.PushProduct.builder()
                    .productId(o.getString("productId").trim())
                    .artifact(o.getString("artifact"))
                    .artifactWin(o.getString("artifactWin"))
                    .build());
        }
        return list;
    }

    /**
     * 解析 runtime。无能力列表时允许缺省。
     * ops-agent 必须给出 agent.cli.bin；pingArgs 缺省为单元素 ping。
     */
    private PluginManifest.RuntimeSpec parseRuntime(JSONObject runtime, boolean hasCapabilities) {
        if (!hasCapabilities) {
            return null;
        }
        if (runtime == null) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "有运行能力时 runtime 必填");
        }
        String kind = runtime.getString("kind");
        if (!"ops-agent".equals(kind) && !"http-adapter".equals(kind) && !"builtin".equals(kind)) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "runtime.kind 非法: " + kind);
        }
        PluginManifest.AgentSpec agent = null;
        if ("ops-agent".equals(kind)) {
            JSONObject agentObj = runtime.getJSONObject("agent");
            if (agentObj == null) {
                throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "ops-agent 须含 agent");
            }
            JSONObject cli = agentObj.getJSONObject("cli");
            if (cli == null || cli.getString("bin") == null || cli.getString("bin").isBlank()) {
                throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "ops-agent 须含 cli.bin");
            }
            List<String> pingArgs = new ArrayList<>();
            JSONArray ping = cli.getJSONArray("pingArgs");
            if (ping != null) {
                for (int i = 0; i < ping.size(); i++) {
                    pingArgs.add(ping.getString(i));
                }
            } else {
                pingArgs.add("ping");
            }
            Map<String, String> argMap = new LinkedHashMap<>();
            JSONObject argMapObj = cli.getJSONObject("argMap");
            if (argMapObj != null) {
                for (String key : argMapObj.keySet()) {
                    String val = argMapObj.getString(key);
                    if (key != null && !key.isBlank() && val != null && !val.isBlank()) {
                        argMap.put(key.trim(), val.trim());
                    }
                }
            }
            agent = PluginManifest.AgentSpec.builder()
                    .bundle(agentObj.getString("bundle"))
                    .cli(PluginManifest.CliSpec.builder()
                            .bin(cli.getString("bin"))
                            .pingArgs(pingArgs)
                            .argMap(argMap)
                            .build())
                    .build();
        }
        return PluginManifest.RuntimeSpec.builder().kind(kind).agent(agent).build();
    }

    private static List<String> readStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            String s = arr.getString(i);
            if (s != null && !s.isBlank()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    /** 将 plugin:id 或裸 id 规范为裸 id。 */
    private static List<String> normalizePluginRefs(List<String> refs) {
        List<String> out = new ArrayList<>();
        for (String ref : refs) {
            String id = stripPluginPrefix(ref);
            if (!id.isBlank()) {
                out.add(id);
            }
        }
        return out;
    }

    static String stripPluginPrefix(String ref) {
        if (ref == null) {
            return "";
        }
        String s = ref.trim();
        if (s.startsWith("plugin:")) {
            return s.substring("plugin:".length()).trim();
        }
        return s;
    }

    /**
     * 去掉 JSONC 中的行注释与块注释，字符串字面量内的斜杠不处理。
     */
    static String stripJsonc(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
            if (inString) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '/' && next == '/') {
                i += 2;
                while (i < input.length() && input.charAt(i) != '\n') {
                    i++;
                }
                if (i < input.length()) {
                    out.append('\n');
                }
                continue;
            }
            if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < input.length() && !(input.charAt(i) == '*' && input.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
