package com.qualitest.web.ai;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 设计流式接口的 SSE 发送与取消标记。
 * <p>
 * 客户端断连、超时或发送失败时只把 cancelled 置为 true，不向业务线程抛异常，
 * 以便 Agent 仍能把已产生的半成品写入会话。
 */
@Slf4j
public final class AiSseStreamSupport {

    private AiSseStreamSupport() {
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
}
