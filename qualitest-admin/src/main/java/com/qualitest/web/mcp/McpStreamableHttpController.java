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
 * 质衡 MCP Streamable HTTP 入口。
 * <p>
 * POST：接收 JSON-RPC 请求，携带项目 id 与 Token 绑定操作者用户 id 后分发处理；
 * GET：在有效会话头下建立 SSE 下行流。
 * 请求须带项目 Token，过滤器校验后把项目用户设置写入请求属性。
 */
@RestController
@Anonymous
@RequiredArgsConstructor
public class McpStreamableHttpController extends ProjectController {

    /** SSE 空闲超时（毫秒） */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    /** JSON-RPC 分发 */
    private final McpJsonRpcDispatcher jsonRpcDispatcher;
    /** MCP 会话登记 */
    private final McpSessionRegistry sessionRegistry;

    /**
     * 处理 MCP JSON-RPC POST。
     * 把当前项目 id、Token 绑定用户 id 一并交给分发器；通知类请求无响应体。
     *
     * @param body 原始 JSON-RPC 文本
     * @return JSON 响应；通知则空 200
     */
    @PostMapping(value = ProjectConstants.MCP_ENDPOINT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handlePost(@RequestBody String body) {
        McpJsonRpcDispatcher.DispatchResult result =
                jsonRpcDispatcher.dispatch(body, getTestProjectId(), getUserId());
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

    /**
     * 建立 MCP SSE 下行流。
     * 会话 id 无效返回 400；超时或完成时移除会话。
     *
     * @param sessionId MCP 会话头
     * @return SSE 发射器或 400
     */
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
