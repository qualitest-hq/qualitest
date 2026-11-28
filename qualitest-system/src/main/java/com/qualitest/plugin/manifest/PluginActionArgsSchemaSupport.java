package com.qualitest.plugin.manifest;

import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.enable.PluginEnableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按插件 palette 贡献上的 argsSchema 校验 pluginAction 节点的 data.config。
 * <p>
 * 画布图校验与 AI 造流提交共用：有 contributionId 且说明书声明了 argsSchema 时才校验；
 * 找不到说明书、贡献不存在或未声明 schema 时不追加错误（兼容手工旧节点）。
 * 环境启用配置用的说明书顶层 configSchema 不在本类处理范围。
 */
@Component
@RequiredArgsConstructor
public class PluginActionArgsSchemaSupport {

    private final PluginEnableService pluginEnableService;
    private final PluginConfigSchemaValidator configSchemaValidator;

    /**
     * 图结构校验入口：把 config 相对 argsSchema 的失败信息写入 errors。
     *
     * @param pathPrefix  报错路径前缀（如节点路径）
     * @param displayName 节点展示名
     * @param data        节点 data 映射
     * @param errors      错误文案收集列表
     */
    public void appendGraphConfigErrors(String pathPrefix, String displayName,
                                        Map<String, Object> data, List<String> errors) {
        if (data == null || errors == null) {
            return;
        }
        String pluginId = str(data.get("pluginId"));
        String contributionId = str(data.get("contributionId"));
        if (pluginId.isEmpty() || contributionId.isEmpty()) {
            return;
        }
        PluginManifest manifest = findManifestQuiet(pluginId);
        if (manifest == null) {
            return;
        }
        PluginPaletteNode node = PluginPaletteSupport.findByContributionId(manifest, contributionId);
        if (node == null || !node.hasArgsSchema()) {
            return;
        }
        Object config = data.get("config");
        if (config == null) {
            config = Map.of();
        }
        try {
            configSchemaValidator.validate(node.getArgsSchema(), config);
        } catch (PluginException e) {
            errors.add(pathPrefix + " 插件步骤「" + displayName + "」config 不符合贡献 "
                    + contributionId + " 的 argsSchema: " + e.getMessage());
        }
    }

    /**
     * 造流 / 写图提交：config 必须通过给定 argsSchema，否则抛 IllegalArgumentException。
     *
     * @param argsSchema  贡献上的 JSON Schema
     * @param config      待校验对象，null 按空对象
     * @param errorPrefix 异常消息前缀，空则使用默认文案
     */
    public void requireConfigMatches(Map<String, Object> argsSchema, Object config, String errorPrefix) {
        try {
            configSchemaValidator.validate(argsSchema, config == null ? Map.of() : config);
        } catch (PluginException e) {
            String prefix = errorPrefix == null || errorPrefix.isBlank()
                    ? "config 不符合 argsSchema: "
                    : errorPrefix;
            throw new IllegalArgumentException(prefix + e.getMessage());
        }
    }

    /** 读已登记说明书；未登记或解析失败返回 null，不向外抛 */
    private PluginManifest findManifestQuiet(String pluginId) {
        try {
            return pluginEnableService.requireManifest(pluginId);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
