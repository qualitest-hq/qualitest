package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;

import java.util.Map;

/**
 * 占位符与运行时路径解析。
 * <p>
 * 模板形态：{@code {{scope.path}}}。<br>
 * 持久变量：{@code env.*}、{@code flow.*}、{@code asset.*}。<br>
 * 最近一次 HTTP：{@code http.body} / {@code http.body.<JsonPath相对路径>}、
 * {@code http.status}、{@code http.duration}、{@code http.header.*}、
 * {@code http.bodyText}、{@code http.responseKind}、{@code http.expectedMatch}。<br>
 * 按节点引用：{@code nodes.<nodeId>.http.…}，从 nodeResponses 读取该节点最后一次响应。<br>
 * LENIENT：未定义占位符替换为空串；STRICT：未定义则抛业务错误码 TF_PLACEHOLDER_UNDEFINED。
 */
public record PlaceholderResolver(ResolveMode mode) {

    public enum ResolveMode {
        /** 未定义占位符 → 空字符串（调试 / Mock） */
        LENIENT,
        /** 未定义占位符 → 抛错（正式 Run） */
        STRICT
    }

    /**
     * 将路径 {@code nodes.<nodeId>.http.<rest>} 拆成节点 id 与 http 相对段。
     *
     * @param nodeId   画布节点 id（{@code nodes.} 与 {@code .http.} 之间的整段）
     * @param httpRest {@code http.} 之后的路径，例如 {@code body.data.code}、{@code status}
     */
    public record NodesHttpRef(String nodeId, String httpRest) {
    }

    /**
     * 宽松模式解析器：占位未定义时替换为空串。
     *
     * @return 解析器实例
     */
    public static PlaceholderResolver lenient() {
        return new PlaceholderResolver(ResolveMode.LENIENT);
    }

    /**
     * 严格模式解析器：占位未定义时抛错。
     *
     * @return 解析器实例
     */
    public static PlaceholderResolver strict() {
        return new PlaceholderResolver(ResolveMode.STRICT);
    }

    /**
     * 替换模板中全部合法路径占位 {@code {{flow.|env.|asset.|http.|nodes.…}}}。
     * 非路径形态的花括号保留为字面量；未定义路径在宽松模式下变空串，严格模式下抛错。
     *
     * @param template 模板原文，null 视为空串
     * @param ctx      当前运行上下文
     * @return 替换后的字符串
     */
    public String resolve(String template, FlowRunContext ctx) {
        if (template == null) {
            return "";
        }
        return MustacheScan.replace(template, inner -> {
            String key = inner.trim();
            Object value = resolvePathSegment(ctx, key);
            if (value == null) {
                if (mode == ResolveMode.STRICT) {
                    throw new FlowExecutionException(
                            FlowErrorCode.TF_PLACEHOLDER_UNDEFINED,
                            "占位符未定义: {{" + key + "}}",
                            key
                    );
                }
                return "";
            }
            return String.valueOf(value);
        });
    }

    /**
     * 单段路径求值：占位符内部、断言/条件左值、比较右值路径共用。
     * 纯 {@code $…} 会先改成 {@code http.body…}；
     * {@code nodes.<id>.http…} 读 nodeResponses；{@code http…} 读 lastResponse。
     *
     * @param ctx  运行上下文
     * @param path 路径原文
     * @return 求值结果；无法解析或快照缺失时返回 null
     */
    public Object resolvePathSegment(FlowRunContext ctx, String path) {
        if (ctx == null || path == null) {
            return null;
        }
        String p = normalizeAssertLeftPath(path.trim());
        if (p.isEmpty()) {
            return null;
        }
        if (p.startsWith("flow.")) {
            return ctx.getFlow().get(p.substring(5));
        }
        if (p.startsWith("env.")) {
            return ctx.getEnv().get(p.substring(4));
        }
        if (p.startsWith("asset.")) {
            return resolveAssetPath(ctx.getAsset(), p.substring(6));
        }
        NodesHttpRef nodesRef = parseNodesHttpPath(p);
        if (nodesRef != null) {
            return resolveHttpPathOnSnapshot(ctx.getNodeResponse(nodesRef.nodeId()), nodesRef.httpRest());
        }
        if (p.startsWith("http.")) {
            return resolveHttpPathOnSnapshot(ctx.getLastResponse(), p.substring(5));
        }
        return null;
    }

    /**
     * 把以 {@code $} 开头的路径改成带 scope 的写法，便于断言左值统一处理。
     * {@code $} → {@code http.body}；{@code $.data.x} → {@code http.body.data.x}；
     * {@code $[0]} → {@code http.body[0]}。其它原样返回。
     */
    public static String normalizeAssertLeftPath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        if (p.isEmpty()) {
            return "";
        }
        if ("$".equals(p)) {
            return "http.body";
        }
        if (p.startsWith("$.")) {
            return "http.body." + p.substring(2);
        }
        if (p.startsWith("$[")) {
            return "http.body" + p.substring(1);
        }
        return p;
    }

    /**
     * 解析 {@code nodes.<nodeId>.http.<rest>}。
     * 须同时含 {@code nodes.} 前缀与 {@code .http.} 分隔；nodeId、httpRest 任一为空则返回 null。
     *
     * @param path 完整路径（通常已经过断言左值规范化）
     * @return 拆分结果；形态不符时为 null
     */
    public static NodesHttpRef parseNodesHttpPath(String path) {
        if (path == null || !path.startsWith("nodes.")) {
            return null;
        }
        String after = path.substring("nodes.".length());
        int httpDot = after.indexOf(".http.");
        if (httpDot <= 0) {
            return null;
        }
        String nodeId = after.substring(0, httpDot).trim();
        String httpRest = after.substring(httpDot + ".http.".length()).trim();
        if (nodeId.isEmpty() || httpRest.isEmpty()) {
            return null;
        }
        return new NodesHttpRef(nodeId, httpRest);
    }

    /**
     * 判断路径是否以 {@code nodes.} 开头。
     * 形态不完整（缺 {@code .http.}）时也为 true，便于校验时单独报错。
     *
     * @param path 待判断路径
     * @return 以 nodes. 开头则为 true
     */
    public static boolean looksLikeNodesPath(String path) {
        return path != null && path.startsWith("nodes.");
    }

    /**
     * 在给定 HTTP 快照上解析相对段（不含 {@code http.} 前缀）。
     * 支持整 body、body 上 JsonPath、bodyText、status、duration、header、responseKind、expectedMatch。
     * {@code body.$…} 非法，返回 null；快照为 null 时各字段均返回 null。
     *
     * @param snap 响应快照
     * @param rest {@code http.} 之后的相对路径
     * @return 字段值或 null
     */
    private static Object resolveHttpPathOnSnapshot(FlowRunContext.HttpResponseSnapshot snap, String rest) {
        if (rest == null || rest.isEmpty()) {
            return null;
        }
        if ("duration".equals(rest)) {
            return snap == null ? null : snap.getDurationMs();
        }
        if ("status".equals(rest)) {
            return snap == null ? null : snap.getStatus();
        }
        if ("responseKind".equals(rest)) {
            return snap == null ? null : snap.getResponseKind();
        }
        if ("expectedMatch".equals(rest)) {
            return snap == null ? null : snap.getExpectedMatch();
        }
        if ("body".equals(rest)) {
            return snap == null ? null : snap.getBody();
        }
        if ("bodyText".equals(rest)) {
            return snap == null ? null : snap.getBodyText();
        }
        if (rest.startsWith("body.")) {
            if (snap == null || snap.getBody() == null) {
                return null;
            }
            String relative = rest.substring(5);
            if (relative.startsWith("$")) {
                return null;
            }
            return JsonPathFacade.eval(snap.getBody(), JsonPathFacade.toAbsolutePath(relative));
        }
        if (rest.startsWith("header.")) {
            return resolveHttpHeader(snap, rest.substring(7));
        }
        if (rest.startsWith("headers.")) {
            return resolveHttpHeader(snap, rest.substring(8));
        }
        return null;
    }

    private static Object resolveHttpHeader(FlowRunContext.HttpResponseSnapshot snap, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (snap == null || snap.getHeaders() == null) {
            return null;
        }
        return snap.getHeaders().get(name);
    }

    /** {@code asset.key} 或 {@code asset.key.a.b}：先取条目，再按点分键逐级下钻。 */
    private static Object resolveAssetPath(Map<String, Object> asset, String rest) {
        if (rest == null || rest.isEmpty()) {
            return null;
        }
        int dot = rest.indexOf('.');
        String key = dot >= 0 ? rest.substring(0, dot) : rest;
        String sub = dot >= 0 ? rest.substring(dot + 1) : "";
        Object val = asset.get(key);
        if (sub.isEmpty()) {
            return val;
        }
        return navigateMap(val, sub);
    }

    /**
     * 对响应 body 求 JsonPath（须以 {@code $} 开头）。
     * 供 HTTP extracts、业务码字段等读取。
     */
    public static Object simpleJsonPath(Object body, String expr) {
        return JsonPathFacade.eval(body, expr);
    }

    /** 仅按 {@code .} 在 Map 上逐级取值（素材嵌套字段，不做 JsonPath）。 */
    @SuppressWarnings("unchecked")
    static Object navigateMap(Object root, String dotPath) {
        if (root == null || dotPath == null || dotPath.isEmpty()) {
            return root;
        }
        Object cur = root;
        for (String part : dotPath.split("\\.")) {
            if (cur == null) {
                return null;
            }
            if (cur instanceof Map<?, ?> map) {
                cur = map.get(part);
            } else {
                return null;
            }
        }
        return cur;
    }
}
