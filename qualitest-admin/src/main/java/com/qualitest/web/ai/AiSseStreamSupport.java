package com.qualitest.web.ai;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.llm.AgentRunListener;
import com.qualitest.ai.llm.LlmClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * AI 设计流式接口的 SSE 发送、取消标记与后台跑设计样板。
 * <p>
 * 客户端断连、超时或发送失败时只把 cancelled 置为 true，不向业务线程抛异常，
 * 以便 Agent 仍能把已产生的半成品写入会话。
 */
@Slf4j
public final class AiSseStreamSupport {

    private AiSseStreamSupport() {
    }

    /**
     * 在后台线程执行一轮设计，并向客户端推送过程事件与结束/错误。
     * 监听回调包含工具起止、文本增量、会话就绪，以及测试流全自动落库与开跑事件。
     *
     * @param timeoutMs            SSE 超时毫秒
     * @param workerThreadName     工作线程名
     * @param errorFallbackMessage 非 LlmClientException 时的错误文案
     * @param task                 实际设计任务（接收 listener 与取消标志）
     * @param <T>                  done 事件中的结果类型
     * @return 已启动的 SseEmitter
     */
    public static <T> SseEmitter openDesignStream(
            long timeoutMs,
            String workerThreadName,
            String errorFallbackMessage,
            DesignStreamTask<T> task) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        armCancel(emitter, cancelled);
        AgentRunListener listener = standardDesignListener(emitter, cancelled);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Thread worker = new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                T result = task.execute(listener, cancelled::get);
                // 即使已取消也尽量推 done（含半成品）；已断连时 sendJson 会静默跳过
                sendJson(emitter, cancelled, Map.of("type", "done", "result", result));
                if (!cancelled.get()) {
                    emitter.complete();
                }
            } catch (Exception e) {
                String message = resolveErrorMessage(e, errorFallbackMessage);
                log.error("{}: {}", workerThreadName, message, e);
                try {
                    sendJson(emitter, cancelled, Map.of("type", "error", "message", message));
                    if (!cancelled.get()) {
                        emitter.complete();
                    }
                } catch (Exception ignored) {
                    if (!cancelled.get()) {
                        emitter.completeWithError(e);
                    }
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        worker.setName(workerThreadName);
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    /**
     * 标准设计 SSE listener：工具起止、思考/正文增量、会话就绪、图提交与 Run 触发。
     */
    public static AgentRunListener standardDesignListener(SseEmitter emitter, AtomicBoolean cancelled) {
        return new AgentRunListener() {
            @Override
            public void onToolStart(String toolName) {
                sendJson(emitter, cancelled, Map.of("type", "tool_start", "tool", toolName));
            }

            @Override
            public void onToolEnd(String toolName) {
                sendJson(emitter, cancelled, Map.of("type", "tool_end", "tool", toolName));
            }

            @Override
            public void onThinkingDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    sendJson(emitter, cancelled, Map.of("type", "thinking", "text", delta));
                }
            }

            @Override
            public void onTextDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    sendJson(emitter, cancelled, Map.of("type", "token", "text", delta));
                }
            }

            @Override
            public void onGraphCommitted(Long testFlowId, Long graphRevision) {
                // 隐式写库成功，推送 testFlowId 与新版本号
                if (testFlowId != null) {
                    java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
                    payload.put("type", "graphCommitted");
                    payload.put("testFlowId", String.valueOf(testFlowId));
                    if (graphRevision != null) {
                        payload.put("graphRevision", graphRevision);
                    }
                    sendJson(emitter, cancelled, payload);
                }
            }

            @Override
            public void onRunStarted(Long runId) {
                // Run 已触发，推送 runId，画布可开始按步骤高亮
                if (runId != null) {
                    sendJson(emitter, cancelled, Map.of(
                            "type", "runStarted",
                            "runId", String.valueOf(runId)));
                }
            }

            @Override
            public void onSessionReady(Long aiChatSessionId) {
                // 尽早推送会话 id，取消后客户端可重拉半成品
                if (aiChatSessionId != null) {
                    sendJson(emitter, cancelled, Map.of(
                            "type", "session",
                            "aiChatSessionId", String.valueOf(aiChatSessionId)));
                }
            }
        };
    }

    /**
     * 在 emitter 完成、超时、出错时把 cancelled 置为 true。
     *
     * @param emitter   当前 SSE 连接
     * @param cancelled 本轮流式请求的取消标志
     */
    public static void armCancel(SseEmitter emitter, AtomicBoolean cancelled) {
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));
    }

    /**
     * 向客户端发送一条 JSON 事件。
     * 若已取消则直接返回；发送 IO 失败时置 cancelled 并吞掉异常。
     *
     * @param emitter   当前 SSE 连接
     * @param cancelled 本轮流式请求的取消标志
     * @param payload   事件体（通常含 type 与业务字段）
     */
    public static void sendJson(SseEmitter emitter, AtomicBoolean cancelled, Map<String, Object> payload) {
        if (cancelled.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .data(JSON.toJSONString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            cancelled.set(true);
            log.debug("SSE 发送失败，标记取消: {}", e.toString());
        }
    }

    /**
     * 从异常链提取可展示给前端的文案。
     * 优先业务异常原文；连接拒绝/超时等给出明确中文；其余带上原始 message，避免只剩笼统兜底句。
     */
    static String resolveErrorMessage(Throwable error, String fallback) {
        if (error instanceof LlmClientException && error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage().trim();
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException) {
                return "无法连接模型服务（连接被拒绝），请检查厂商 Base URL 是否可达、网关是否已启动";
            }
            if (t instanceof java.net.SocketTimeoutException
                    || t instanceof java.util.concurrent.TimeoutException) {
                return "连接模型服务超时，请检查网络或增大读超时";
            }
            if (t instanceof java.net.UnknownHostException) {
                return "无法解析模型服务地址，请检查厂商 Base URL";
            }
            String name = t.getClass().getSimpleName();
            String msg = t.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (name.contains("Http") || name.contains("OpenAi") || name.contains("LangChain")) {
                    return "模型调用失败: " + msg.trim();
                }
            }
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage().trim();
        }
        return fallback != null ? fallback : "AI 助手请求失败";
    }

    /**
     * 一轮 SSE 设计任务：由业务 Agent 执行，结果作为 done 事件载荷。
     *
     * @param <T> 设计结果类型
     */
    @FunctionalInterface
    public interface DesignStreamTask<T> {
        /**
         * @param listener  过程事件回调
         * @param cancelled 客户端是否已取消
         * @return 完整设计结果
         */
        T execute(AgentRunListener listener, BooleanSupplier cancelled) throws Exception;
    }
}
