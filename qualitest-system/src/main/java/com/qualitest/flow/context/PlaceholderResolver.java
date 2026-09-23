package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;

import java.util.Map;

/**
 * 占位符与运行时路径解析。
 * <p>
 * 模板形态：{@code {{scope.path}}}。<br>
 * 持久变量：{@code env.*}、{@code flow.*}、{@code asset.*}。<br>
 * 上一步 HTTP：{@code http.body} / {@code http.body.<JsonPath相对路径>}、
 * {@code http.status}、{@code http.duration}、{@code http.header.*}。<br>
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
     * 替换模板中全部合法路径占位 {@code {{flow.|env.|asset.|http.…}}}。
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
     * 单段路径求值：占位符内部、断言左值、条件左值共用。
     * 若写成纯 {@code $…}，会先改成 {@code http.body…} 再解析。
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
        if (p.startsWith("http.")) {
            return resolveHttpPath(ctx, p.substring(5));
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
     * 解析 {@code http.*}：整 body、body 上 JsonPath、状态码、耗时、响应头。
     * {@code http.body.$.…} 视为非法，返回 null。
     */
    private static Object resolveHttpPath(FlowRunContext ctx, String rest) {
        if (rest == null || rest.isEmpty()) {
            return null;
        }
        FlowRunContext.HttpResponseSnapshot last = ctx.getLastResponse();
        if ("duration".equals(rest)) {
            return last == null ? null : last.getDurationMs();
        }
        if ("status".equals(rest)) {
            return last == null ? null : last.getStatus();
        }
        if ("body".equals(rest)) {
            return last == null ? null : last.getBody();
        }
        if (rest.startsWith("body.")) {
            if (last == null || last.getBody() == null) {
                return null;
            }
            String relative = rest.substring(5);
            if (relative.startsWith("$")) {
                return null;
            }
            return JsonPathFacade.eval(last.getBody(), JsonPathFacade.toAbsolutePath(relative));
        }
        if (rest.startsWith("header.")) {
            return resolveHttpHeader(ctx, rest.substring(7));
        }
        if (rest.startsWith("headers.")) {
            return resolveHttpHeader(ctx, rest.substring(8));
        }
        return null;
    }

    private static Object resolveHttpHeader(FlowRunContext ctx, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        FlowRunContext.HttpResponseSnapshot last = ctx.getLastResponse();
        if (last == null || last.getHeaders() == null) {
            return null;
        }
        return last.getHeaders().get(name);
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
