package com.qualitest.web.mcp;

import com.qualitest.ai.mcp.protocol.McpJsonRpcDispatcher;
import com.qualitest.ai.mcp.protocol.McpSessionRegistry;
import com.qualitest.common.annotation.Anonymous;
import com.qualitest.common.constant.ProjectConstants;
import com.qualitest.common.core.controller.ProjectController;
import com.qualitest.common.mcp.McpJsonRpc;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 质衡 MCP Streamable HTTP 入口（HTTP 适配层）。
 * <p>
 * 对外暴露 {@link ProjectConstants#MCP_ENDPOINT}：
 * <ul>
 *   <li>{@code POST}：接收 JSON-RPC 请求体，委托 {@link McpJsonRpcDispatcher} 处理</li>
 *   <li>{@code GET}：在携带有效 {@code Mcp-Session-Id} 时建立 SSE 下行流</li>
 * </ul>
 * 请求须带 {@code X-Project-Token}，由过滤器校验后将项目上下文写入请求属性。
 */
@RestController
@Anonymous
@RequiredArgsConstructor
public class McpStreamableHttpController extends ProjectController {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final McpJsonRpcDispatcher jsonRpcDispatcher;
    private final McpSessionRegistry sessionRegistry;

    @PostMapping(value = ProjectConstants.MCP_ENDPOINT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handlePost(@RequestBody String body) {
        McpJsonRpcDispatcher.DispatchResult result =
                jsonRpcDispatcher.dispatch(body, getTestProjectId());
        if (result.isNotification()) {
            return ResponseEntity.ok().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (result.getSessionId() != null) {
            headers.set(McpJsonRpc.SESSION_HEADER, result.getSessionId());
        }
        return new ResponseEntity<>(result.getResponseBody(), headers, HttpStatus.OK);
    }

    @GetMapping(value = ProjectConstants.MCP_ENDPOINT, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> handleGet(
            @RequestHeader(value = McpJsonRpc.SESSION_HEADER, required = false) String sessionId) {
        if (!sessionRegistry.isValid(sessionId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> sessionRegistry.remove(sessionId));
        emitter.onTimeout(() -> {
            sessionRegistry.remove(sessionId);
            emitter.complete();
        });
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(emitter);
    }
}
