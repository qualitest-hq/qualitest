package com.qualitest.flow.delivery;

/**
 * 开放 HTTP 握连接时的写出端。
 * <p>
 * 场景主图与子流 Run 通常不挂本接口；
 * 开放入站在开跑前把实现（如 DeferredResult 封装）塞进运行上下文，
 * {@code http-return} 或收束交付时调用 {@link #complete} 把定稿出参写成 HTTP 响应。
 * 客户端超时或取消后图仍继续跑：{@link #complete} 返回 false，不抛错中断跑图。
 */
public interface DeliverySink {

    /**
     * 将定稿出参写出给调用方（如 DeferredResult 响应体）。
     * 实现方应对「多次 complete」只认第一次；连接已断或写出失败时返回 false。
     *
     * @param out 定稿出参（HTTP 写出前可由交付器补默认 status / Content-Type）
     * @return true 写出成功；false 连接已断或写出失败
     */
    boolean complete(OutPayload out);

    /**
     * 调用方连接是否仍可用。
     * 仅作观测；真正写出以 {@link #complete} 的返回值为准。
     */
    boolean isConnected();
}
