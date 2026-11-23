package com.qualitest.web.controller.openhttp;

import com.qualitest.flow.openhttp.OpenHttpIngressService;
import com.qualitest.flow.openhttp.OpenHttpRouteCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放 HTTP 入站 Controller。
 * <p>
 * 映射 {@code /open-http/{projectId}/**}：摘出业务 path、query、headers、body，
 * 交给 {@link OpenHttpIngressService} 握连接跑图。
 * 平台安全配置对本路径放行匿名；鉴权逻辑放在流图业务节点内完成。
 */
@RestController
@RequestMapping("/open-http")
public class OpenHttpIngressController {

    private final OpenHttpIngressService openHttpIngressService;

    public OpenHttpIngressController(OpenHttpIngressService openHttpIngressService) {
        this.openHttpIngressService = openHttpIngressService;
    }

    /**
     * 任意 method 命中 {@code /open-http/{projectId}/...}。
     * body 可选；无 body 的 GET 等请求 {@code body} 为 null。
     */
    @RequestMapping("/{projectId}/**")
    public DeferredResult<ResponseEntity<Object>> ingress(
            @PathVariable("projectId") Long projectId,
            @RequestBody(required = false) Object body,
            HttpServletRequest request) {
        String path = extractPath(request, projectId);
        Map<String, Object> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length == 1) {
                query.put(k, v[0]);
            } else if (v != null) {
                query.put(k, v);
            }
        });
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.put(name, request.getHeader(name));
            }
        }
        return openHttpIngressService.handle(
                projectId,
                OpenHttpRouteCatalog.normalizeMethod(request.getMethod()),
                path,
                query,
                headers,
                body);
    }

    /**
     * 从完整 URI 去掉 contextPath + /open-http/{projectId}，得到业务 path。
     */
    private static String extractPath(HttpServletRequest request, Long projectId) {
        String uri = request.getRequestURI();
        String prefix = request.getContextPath() + "/open-http/" + projectId;
        String path;
        if (uri != null && uri.startsWith(prefix)) {
            path = uri.substring(prefix.length());
        } else {
            path = "/";
        }
        if (path.isEmpty()) {
            path = "/";
        }
        return OpenHttpRouteCatalog.normalizePath(path);
    }
}
