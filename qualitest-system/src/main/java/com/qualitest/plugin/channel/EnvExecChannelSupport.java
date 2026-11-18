package com.qualitest.plugin.channel;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.service.ITestPluginEnvBindingService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 从环境已启用且说明书 channelProvider=true 的绑定解析执行通道。
 * 找到且 baseUrl 非空 → exec-agent；否则 local-exec。
 */
@Component
@RequiredArgsConstructor
public class EnvExecChannelSupport {

    private final ITestPluginEnvBindingService bindingService;
    private final PluginEnableService enableService;

    /**
     * 解析当前环境的执行通道端点；无通道提供者时 kind=local-exec。
     */
    public ChannelEndpoint resolve(Long envId) {
        if (envId == null) {
            return ChannelEndpoint.local();
        }
        TestPluginEnvBinding binding = findChannelProviderBinding(envId);
        if (binding == null) {
            return ChannelEndpoint.local();
        }
        JSONObject cfg = parseConfig(binding.getConfigJson());
        String baseUrl = resolveBaseUrl(cfg);
        if (baseUrl == null || baseUrl.isBlank()) {
            return ChannelEndpoint.local();
        }
        String token = resolveToken(cfg);
        return ChannelEndpoint.builder()
                .kind(ExecChannelKind.EXEC_AGENT)
                .pluginId(binding.getPluginId())
                .baseUrl(baseUrl.trim())
                .token(token == null ? "" : token.trim())
                .build();
    }

    /**
     * 从执行代理配置解析 baseUrl：优先顶层，其次 channel.execAgent / channel。
     */
    public static String resolveBaseUrl(JSONObject cfg) {
        if (cfg == null) {
            return null;
        }
        return firstNonBlank(cfg.getString("baseUrl"), nestedBaseUrl(cfg));
    }

    /**
     * 从执行代理配置解析 token：优先顶层，其次 channel.execAgent / channel。
     */
    public static String resolveToken(JSONObject cfg) {
        if (cfg == null) {
            return null;
        }
        return firstNonBlank(cfg.getString("token"), nestedToken(cfg));
    }

    /**
     * 要求当前环境为可达的 exec-agent；否则抛 AGENT_UNREACHABLE / CONFIG。
     */
    public ChannelEndpoint requireExecAgent(Long envId) {
        ChannelEndpoint ep = resolve(envId);
        if (ep.getKind() != ExecChannelKind.EXEC_AGENT) {
            throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE,
                    "当前环境未启用执行通道插件或未配置 baseUrl");
        }
        if (ep.getPluginId() == null || !enableService.isEnabled(envId, ep.getPluginId())) {
            throw new PluginException(PluginErrorCode.PROVIDER_NOT_FOUND, "执行通道插件未启用");
        }
        return ep;
    }

    private TestPluginEnvBinding findChannelProviderBinding(Long envId) {
        List<TestPluginEnvBinding> rows = bindingService.selectTestPluginEnvBindingList(
                TestPluginEnvBinding.builder()
                        .testProjectEnvId(envId)
                        .enabled(1)
                        .build());
        if (rows == null) {
            return null;
        }
        for (TestPluginEnvBinding row : rows) {
            if (row == null || row.getDelStatus() == null || row.getDelStatus() != 0) {
                continue;
            }
            if (row.getPluginId() == null || row.getPluginId().isBlank()) {
                continue;
            }
            try {
                PluginManifest manifest = enableService.requireManifest(row.getPluginId());
                if (manifest.isChannelProvider()) {
                    return row;
                }
            } catch (PluginException ignored) {
                // 包未登记则跳过
            }
        }
        return null;
    }

    private static String nestedBaseUrl(JSONObject cfg) {
        JSONObject channel = cfg.getJSONObject("channel");
        if (channel == null) {
            return null;
        }
        JSONObject execAgent = channel.getJSONObject("execAgent");
        if (execAgent != null && execAgent.getString("baseUrl") != null) {
            return execAgent.getString("baseUrl");
        }
        return channel.getString("baseUrl");
    }

    private static String nestedToken(JSONObject cfg) {
        JSONObject channel = cfg.getJSONObject("channel");
        if (channel == null) {
            return null;
        }
        JSONObject execAgent = channel.getJSONObject("execAgent");
        if (execAgent != null && execAgent.getString("token") != null) {
            return execAgent.getString("token");
        }
        return channel.getString("token");
    }

    private static JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject obj = JSON.parseObject(configJson);
            return obj == null ? new JSONObject() : obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    /**
     * 解析后的执行通道端点：本机直跑 CLI，或经执行代理转发。
     */
    @Getter
    @Builder
    public static class ChannelEndpoint {
        /** 通道种类 */
        private final ExecChannelKind kind;
        /** 提供通道的插件 id；本机通道可为空 */
        private final String pluginId;
        /** 执行代理基址；本机通道为空串 */
        private final String baseUrl;
        /** 执行代理鉴权令牌；本机通道为空串 */
        private final String token;

        /** 构造本机直跑通道（无代理地址）。 */
        public static ChannelEndpoint local() {
            return ChannelEndpoint.builder().kind(ExecChannelKind.LOCAL_EXEC).pluginId(null).baseUrl("").token("").build();
        }
    }
}
