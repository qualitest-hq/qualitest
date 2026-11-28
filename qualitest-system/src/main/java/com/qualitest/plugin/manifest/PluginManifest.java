package com.qualitest.plugin.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 解析后的插件说明书内存模型。
 * 字段来自包根目录说明书文件；rawJson 为去掉注释后的规范 JSON 快照，用于登记与指纹。
 */
@Getter
@Builder
public class PluginManifest {

    /** 插件唯一标识 */
    private final String id;
    /** 语义化版本；调用前会与被测端 CLI ping 返回的 version 比对是否相同 */
    private final String version;
    /** 界面展示名 */
    private final String displayName;
    /** 备注说明 */
    private final String remarks;
    /** 对外能力列表 */
    private final List<Capability> capabilities;
    /** 依赖的其它插件 id（已去掉 plugin: 前缀） */
    private final List<String> requires;
    /** 隔离域标记；启用互斥仅认 ui-slot: 前缀 */
    private final List<String> isolation;
    /** 跑流挂点：挂点名 → 调用的本包能力 */
    private final List<Hook> hooks;
    /** 项目级挂载声明（可选） */
    private final ProjectMountSpec projectMount;
    /** 环境配置 JSON Schema（可选） */
    private final Map<String, Object> configSchema;
    /** 经执行代理可推送的制品 */
    private final List<PushProduct> pushProducts;
    /** 是否可作为 exec-agent 通道提供者 */
    private final boolean channelProvider;
    /** 前端贡献，如 chrome.badges、env */
    private final Map<String, Object> ui;
    /**
     * AI / MCP 工具贡献（说明书顶层 ai）。
     * 典型：ai.tools[] 声明 Function Tool，由平台发现后进造流助手与 MCP。
     */
    private final Map<String, Object> ai;
    /** 运行时形态（ops-agent / http-adapter / builtin） */
    private final RuntimeSpec runtime;
    /** 规范化后的说明书 JSON 原文 */
    private final String rawJson;

    /** 返回能力列表；说明书未声明时为空列表而非 null。 */
    public List<Capability> getCapabilities() {
        return capabilities == null ? Collections.emptyList() : capabilities;
    }

    /** 返回依赖插件 id 列表。 */
    public List<String> getRequires() {
        return requires == null ? Collections.emptyList() : requires;
    }

    /** 返回隔离域标记列表；未声明时为空列表而非 null。 */
    public List<String> getIsolation() {
        return isolation == null ? Collections.emptyList() : isolation;
    }

    /** 返回跑流挂点列表；未声明时为空列表而非 null。 */
    public List<Hook> getHooks() {
        return hooks == null ? Collections.emptyList() : hooks;
    }

    /** 返回推送制品列表。 */
    public List<PushProduct> getPushProducts() {
        return pushProducts == null ? Collections.emptyList() : pushProducts;
    }

    /** 按能力名查找；不存在返回 null。 */
    public Capability findCapability(String name) {
        if (name == null) {
            return null;
        }
        for (Capability c : getCapabilities()) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    /** 是否声明了指定挂点名。 */
    public boolean hasHook(String hookName) {
        if (hookName == null) {
            return false;
        }
        for (Hook h : getHooks()) {
            if (hookName.equals(h.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 单条能力声明。 */
    @Getter
    @Builder
    public static class Capability {
        /** 能力名，如 archive.create */
        private final String name;
        /** 读写效应：read / write 等 */
        private final String effect;
        /** 总闸：none 或 destructive；空视为 none */
        private final String gate;

        /** 解析后的总闸名；未声明时按 none。 */
        public String resolvedGate() {
            return gate == null || gate.isBlank() ? "none" : gate;
        }
    }

    /** 跑流挂点：在何时触发本包哪些能力。 */
    @Getter
    @Builder
    public static class Hook {
        /** 挂点名，如 onCheckpoint、onRestore */
        private final String name;
        /** 同挂点多插件时的排序，数值越小越靠前 */
        private final int order;
        /** 本包能力名列表，须在 capabilities 内 */
        private final List<String> actions;
    }

    /** 项目级挂载声明。 */
    @Getter
    @Builder
    public static class ProjectMountSpec {
        /** 字段列表 */
        private final List<MountField> fields;
        /** 挂载字段注入到环境 config 的路径 */
        private final List<MountInject> inject;
        /** 复用另一插件的项目挂载（如造数复用存档点） */
        private final String inheritFrom;

        public List<MountField> getFields() {
            return fields == null ? Collections.emptyList() : fields;
        }

        public List<MountInject> getInject() {
            return inject == null ? Collections.emptyList() : inject;
        }
    }

    /** 项目挂载字段。 */
    @Getter
    @Builder
    public static class MountField {
        private final String name;
        private final boolean required;
        private final String pattern;
    }

    /** 挂载字段注入到 config 的映射。 */
    @Getter
    @Builder
    public static class MountInject {
        /** 挂载 JSON 中的字段名 */
        private final String from;
        /** 写入 config 的点分路径，如 db.bindAlias */
        private final String to;
    }

    /** 推送制品。 */
    @Getter
    @Builder
    public static class PushProduct {
        private final String productId;
        private final String artifact;
        private final String artifactWin;
    }

    /** 运行时规格。 */
    @Getter
    @Builder
    public static class RuntimeSpec {
        /** 运行形态：ops-agent 表示本机/被测旁 CLI */
        private final String kind;
        /** ops-agent 时的代理与 CLI 描述 */
        private final AgentSpec agent;
    }

    /** 被测旁代理描述。 */
    @Getter
    @Builder
    public static class AgentSpec {
        /** 捆绑方式，如 embedded */
        private final String bundle;
        /** CLI 可执行文件与探活参数 */
        private final CliSpec cli;
    }

    /** CLI 调用规格。 */
    @Getter
    @Builder
    public static class CliSpec {
        /** 命令名或可执行文件名，如 sdap */
        private final String bin;
        /** 探活参数列表，默认仅 ping */
        private final List<String> pingArgs;
        /** Invoke args 键 → CLI 开关，如 saveId → --id */
        private final Map<String, String> argMap;

        public Map<String, String> getArgMap() {
            return argMap == null ? Collections.emptyMap() : argMap;
        }
    }
}
