package com.qualitest.flow.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 进程内外部变更事件总线。
 * 按 testFlowId / testProjectId 登记监听；写库成功后扇出给 SSE 等订阅方。
 */
@Slf4j
@Component
public class FlowExternalChangeHub {

    /** 按测试流订阅（图 / Run / 流元数据） */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<FlowExternalChangeEvent>>> byFlow =
            new ConcurrentHashMap<>();

    /** 按项目订阅（素材 / 鉴权 / 环境；画布页也可同时订流） */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<FlowExternalChangeEvent>>> byProject =
            new ConcurrentHashMap<>();

    /**
     * 订阅某测试流的变更。
     *
     * @return 取消订阅的 Runnable
     */
    public Runnable subscribeFlow(Long testFlowId, Consumer<FlowExternalChangeEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        if (testFlowId == null) {
            return () -> {
            };
        }
        byFlow.computeIfAbsent(testFlowId, id -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> unsubscribe(byFlow, testFlowId, listener);
    }

    /**
     * 订阅某项目的资源域变更。
     *
     * @return 取消订阅的 Runnable
     */
    public Runnable subscribeProject(Long testProjectId, Consumer<FlowExternalChangeEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        if (testProjectId == null) {
            return () -> {
            };
        }
        byProject.computeIfAbsent(testProjectId, id -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> unsubscribe(byProject, testProjectId, listener);
    }

    /** 发布事件到本进程订阅者（按变更域路由，避免同连接双推） */
    public void publish(FlowExternalChangeEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        if (isProjectDomain(event.getType())) {
            if (event.getTestProjectId() != null) {
                fanout(byProject.get(event.getTestProjectId()), event);
            }
            return;
        }
        if (event.getTestFlowId() != null) {
            fanout(byFlow.get(event.getTestFlowId()), event);
        }
    }

    /** 素材 / 鉴权 / 环境属项目域；其余（图 / Run / 流元数据）属流域 */
    private static boolean isProjectDomain(String type) {
        return FlowExternalChangeEvent.TYPE_ASSET_VARIABLES_CHANGED.equals(type)
                || FlowExternalChangeEvent.TYPE_AUTH_CONFIG_CHANGED.equals(type)
                || FlowExternalChangeEvent.TYPE_PROJECT_ENVS_CHANGED.equals(type);
    }

    private static void fanout(List<Consumer<FlowExternalChangeEvent>> listeners,
                               FlowExternalChangeEvent event) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (Consumer<FlowExternalChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.debug("外部变更监听失败 type={}: {}", event.getType(), e.toString());
            }
        }
    }

    private static void unsubscribe(
            ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<FlowExternalChangeEvent>>> map,
            Long key,
            Consumer<FlowExternalChangeEvent> listener) {
        CopyOnWriteArrayList<Consumer<FlowExternalChangeEvent>> list = map.get(key);
        if (list == null) {
            return;
        }
        list.remove(listener);
        if (list.isEmpty()) {
            map.remove(key, list);
        }
    }
}
