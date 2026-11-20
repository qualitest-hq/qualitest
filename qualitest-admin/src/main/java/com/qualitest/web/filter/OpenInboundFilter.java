package com.qualitest.web.filter;

import com.alibaba.fastjson2.JSON;
import com.qualitest.flow.inbound.OpenInboundExecutionService;
import com.qualitest.flow.inbound.OpenInboundOutFlush;
import com.qualitest.flow.inbound.OpenInboundPathIndex;
import com.qualitest.flow.inbound.OpenInboundPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 开放入站入口：在安全链之前按完整 path 查索引，命中则握连接跑图并写响应，未命中放行。
 */
public class OpenInboundFilter extends OncePerRequestFilter implements Ordered {

    private final OpenInboundPathIndex pathIndex;
    private final OpenInboundExecutionService openInboundExecutionService;

    public OpenInboundFilter(OpenInboundPathIndex pathIndex,
                             OpenInboundExecutionService openInboundExecutionService) {
        this.pathIndex = pathIndex;
        this.openInboundExecutionService = openInboundExecutionService;
    }

    /** 早于 Spring Security（约 -100），保证匿名入站不被 JWT 拦下 */
    public static final int FILTER_ORDER = -200;

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = resolvePath(request);
        if (path.isEmpty() || pathIndex.findFlowId(path) == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String contentType = request.getContentType();
        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

        OpenInboundOutFlush.FlushResult result = openInboundExecutionService.handle(path, body, contentType);
        writeFlush(response, result);
    }

    private static String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.isEmpty()) {
            uri = "/";
        }
        return OpenInboundPaths.normalize(uri);
    }

    private static void writeFlush(HttpServletResponse response, OpenInboundOutFlush.FlushResult result)
            throws IOException {
        response.setStatus(result.status());
        if (result.headers() != null) {
            for (Map.Entry<String, String> h : result.headers().entrySet()) {
                response.setHeader(h.getKey(), h.getValue());
            }
        }
        Object payload = result.body();
        byte[] bytes;
        if (payload instanceof byte[] raw) {
            bytes = raw;
        } else if (payload instanceof String s) {
            bytes = s.getBytes(StandardCharsets.UTF_8);
            if (response.getHeader("Content-Type") == null) {
                response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
            }
        } else {
            bytes = JSON.toJSONBytes(payload);
            if (response.getHeader("Content-Type") == null) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            }
        }
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
