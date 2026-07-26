package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符解析：{@code {{scope.path}}}
 * <p>
 * 持久 scope：{@code env.*}、{@code flow.*}、{@code asset.*}；
 * 上一步 HTTP 快照：{@code http.body.*}、{@code http.status}、{@code http.duration}、{@code http.header.*}。
 * <p>
 * LENIENT：未定义占位符 → 空串；STRICT：未定义 → {@link FlowErrorCode#TF_PLACEHOLDER_UNDEFINED}
 */
public record PlaceholderResolver(ResolveMode mode) {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    public enum ResolveMode {
        /**
         * 场景运行 / Mock：未定义占位符 → 空字符串
         */
        LENIENT,
        /**
         * 正式 Run：未定义 → 抛错
         */
        STRICT
    }

    public static PlaceholderResolver lenient() {
        return new PlaceholderResolver(ResolveMode.LENIENT);
    }

    public static PlaceholderResolver strict() {
        return new PlaceholderResolver(ResolveMode.STRICT);
    }

    /**
     * 替换模板中全部 {@code {{…}}} 占位符
     */
    public String resolve(String template, FlowRunContext ctx) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String inner = matcher.group(1).trim();
            Object value = resolvePathSegment(ctx, inner);
            if (value == null) {
                if (mode == ResolveMode.STRICT) {
                    throw new FlowExecutionException(
                            FlowErrorCode.TF_PLACEHOLDER_UNDEFINED,
                            "占位符未定义: {{" + inner + "}}",
                            inner
                    );
                }
                matcher.appendReplacement(out, "");
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * 单段路径求值：占位符 inner、断言左值、条件左值共用。
     */
    public Object resolvePathSegment(FlowRunContext ctx, String path) {
        if (ctx == null || path == null) {
            return null;
        }
        String p = path.trim();
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
        // 上一步 HTTP 快照
        if (p.startsWith("http.")) {
            return resolveHttpPath(ctx, p.substring(5));
        }
        return null;
    }

    /**
     * {@code http.*} 路径：body / status / duration / header(s)
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
        if (rest.startsWith("body.")) {
            if (last == null || last.getBody() == null) {
                return null;
            }
            return navigate(last.getBody(), rest.substring(5));
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
        return navigate(val, sub);
    }

    /**
     * 简化 JsonPath：仅支持 {@code $.a.b.c} 点分路径。
     * 用于 HTTP extracts 与业务码字段读取。
     */
    public static Object simpleJsonPath(Object body, String expr) {
        if (expr == null || !expr.startsWith("$.")) {
            return null;
        }
        return navigate(body, expr.substring(2));
    }

    @SuppressWarnings("unchecked")
    static Object navigate(Object root, String dotPath) {
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
