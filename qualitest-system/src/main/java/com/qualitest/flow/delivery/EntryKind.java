package com.qualitest.flow.delivery;

import com.qualitest.flow.validate.FlowNodeType;

/**
 * 本次 Run 从哪种入口进入。
 * <p>
 * 与 trigger（ui / suite / http / mcp 等「谁发起」）相互独立记录：
 * 同一张场景图既可由界面触发，也可由套件触发，entryKind 仍是 scenario。
 * <ul>
 *   <li>{@link #SCENARIO} — 场景主图：不提供返回节点；收束不强制对外交付</li>
 *   <li>{@link #SUBFLOW} — 父图 subflow 拉起的子流：{@code subflow-return} 或收束时把 out 挂到父图节点</li>
 *   <li>{@link #HTTP} — 开放 HTTP 握连接：{@code http-return} 或收束时经 DeliverySink 写出</li>
 * </ul>
 * 本枚举同时承载「入口节点 type ↔ 返回节点 type ↔ 是否对外交付」的对照：
 * {@link #forEntryNodeType}、{@link #forReturnNodeType}、{@link #returnNodeTypeCode}、
 * {@link #expectedReturnTypeForEntry}、{@link #deliversExternally}。
 */
public enum EntryKind {

    /** 场景主图入口 */
    SCENARIO("scenario"),
    /** 子流图入口（由父图 subflow 节点调用） */
    SUBFLOW("subflow"),
    /** 开放 HTTP 入站入口 */
    HTTP("http");

    /** 写入运行上下文 entryKind 字段的字符串码 */
    private final String code;

    /** @param code 写入上下文的 entryKind 字符串 */
    EntryKind(String code) {
        this.code = code;
    }

    /** 写入运行上下文 / 持久化用的字符串码 */
    public String getCode() {
        return code;
    }

    /**
     * 由字符串还原枚举；空串或无法识别时按 {@link #SCENARIO}。
     */
    public static EntryKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SCENARIO;
        }
        String c = code.trim().toLowerCase();
        for (EntryKind k : values()) {
            if (k.code.equals(c)) {
                return k;
            }
        }
        return SCENARIO;
    }

    /**
     * 路径走尽仍未交付时，是否必须用入口上的 {@code fallbackOut} 补交一次。
     * 仅 {@link #HTTP}、{@link #SUBFLOW} 为 true；场景为 false。
     */
    public boolean requiresFallbackOut() {
        return this == HTTP || this == SUBFLOW;
    }

    /**
     * 是否会对外写出或挂载父节点。
     * {@link #HTTP}、{@link #SUBFLOW} 为 true；场景为 false。
     */
    public boolean deliversExternally() {
        return requiresFallbackOut();
    }

    /**
     * 本入口种类应使用的返回节点 type 字符串。
     * HTTP → {@code http-return}；SUBFLOW → {@code subflow-return}；场景无返回节点 → null。
     */
    public String returnNodeTypeCode() {
        return switch (this) {
            case HTTP -> FlowNodeType.HTTP_RETURN.getCode();
            case SUBFLOW -> FlowNodeType.SUBFLOW_RETURN.getCode();
            case SCENARIO -> null;
        };
    }

    /**
     * 画布入口节点 type 字符串 → 本枚举。
     * {@code http-entry} / {@code subflow-entry} / {@code scenario-entry}；其它返回 null。
     */
    public static EntryKind forEntryNodeType(String entryType) {
        if (FlowNodeType.HTTP_ENTRY.matches(entryType)) {
            return HTTP;
        }
        if (FlowNodeType.SUBFLOW_ENTRY.matches(entryType)) {
            return SUBFLOW;
        }
        if (FlowNodeType.SCENARIO_ENTRY.matches(entryType)) {
            return SCENARIO;
        }
        return null;
    }

    /**
     * 返回节点 type 字符串 → 本枚举。
     * {@code http-return} → HTTP；{@code subflow-return} → SUBFLOW；其它返回 null。
     */
    public static EntryKind forReturnNodeType(String returnType) {
        if (FlowNodeType.HTTP_RETURN.matches(returnType)) {
            return HTTP;
        }
        if (FlowNodeType.SUBFLOW_RETURN.matches(returnType)) {
            return SUBFLOW;
        }
        return null;
    }

    /**
     * 给定入口节点 type，算出该入口可达子图上允许的返回节点 type。
     * 场景入口或非入口 → null（场景图不应出现返回节点）。
     */
    public static String expectedReturnTypeForEntry(String entryType) {
        EntryKind kind = forEntryNodeType(entryType);
        return kind == null ? null : kind.returnNodeTypeCode();
    }
}
