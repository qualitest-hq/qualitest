package com.qualitest.flow.delivery;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把定稿 out 写成 Spring 异步 {@link DeferredResult} 的 HTTP 响应。
 * <p>
 * 首次 {@link #complete} 成功后置位，后续调用直接视为已完成。
 * 超时、错误或主动 {@link #markDisconnected} 后，complete 返回 false，
 * 表示写出失败，但不阻止跑图线程继续执行后续节点。
 */
public final class DeferredResultDeliverySink implements DeliverySink {

    private final DeferredResult<ResponseEntity<Object>> deferred;
    /** 是否已向 DeferredResult 提交过结果（含失败路径） */
    private final AtomicBoolean completed = new AtomicBoolean(false);
    /** 客户端连接是否仍视为可用 */
    private final AtomicBoolean connected = new AtomicBoolean(true);

    /** 绑定异步 HTTP 响应容器，并注册错误时断开写出端的回调 */
    public DeferredResultDeliverySink(DeferredResult<ResponseEntity<Object>> deferred) {
        this.deferred = deferred;
        if (deferred != null) {
            deferred.onError(t -> connected.set(false));
            deferred.onCompletion(() -> {
                // 超时/取消完成后也可能走到这里；连接标记由 onTimeout / markDisconnected 维护
            });
        }
    }

    /**
     * 标记调用方已断开（异步超时、传输错误等）。
     * 图执行线程不因此中断；仅使后续 complete 返回 false。
     */
    public void markDisconnected() {
        connected.set(false);
    }

    @Override
    public boolean isConnected() {
        return connected.get() && deferred != null && !deferred.isSetOrExpired();
    }

    /**
     * 组装 ResponseEntity 并 setResult。
     * status 限制在 100–599；缺省按 200。headers 中 null 值跳过。
     */
    @Override
    public boolean complete(OutPayload out) {
        if (!completed.compareAndSet(false, true)) {
            return true;
        }
        if (deferred == null || deferred.isSetOrExpired() || !connected.get()) {
            return false;
        }
        try {
            int status = out.getStatus() != null ? out.getStatus() : 200;
            HttpHeaders headers = new HttpHeaders();
            if (out.getHeaders() != null) {
                for (Map.Entry<String, Object> e : out.getHeaders().entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        headers.add(e.getKey(), String.valueOf(e.getValue()));
                    }
                }
            }
            int code = Math.min(599, Math.max(100, status));
            ResponseEntity<Object> entity = ResponseEntity.status(code)
                    .headers(headers)
                    .body(out.getBody());
            return deferred.setResult(entity);
        } catch (Exception e) {
            connected.set(false);
            return false;
        }
    }
}
