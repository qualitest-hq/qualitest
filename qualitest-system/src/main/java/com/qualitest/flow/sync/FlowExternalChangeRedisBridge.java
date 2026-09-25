package com.qualitest.flow.sync;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 多实例部署时，用 Redis 频道把外部变更通知扇出到其它应用节点。
 * 本机订阅者已由内存总线直推；收到带本实例 id 的回声消息时跳过，避免重复推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowExternalChangeRedisBridge implements MessageListener {

    /** Redis 频道名 */
    public static final String CHANNEL = "qualitest:flow:external-change";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final FlowExternalChangeHub hub;

    /** 本进程唯一 id，用于过滤自己发出的回声 */
    private final String instanceId = UUID.randomUUID().toString();

    /** 启动时订阅频道 */
    @PostConstruct
    public void subscribe() {
        try {
            redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        } catch (Exception e) {
            log.warn("外部变更 Redis 订阅失败: {}", e.toString());
        }
    }

    /** 关闭时取消订阅 */
    @PreDestroy
    public void unsubscribe() {
        try {
            redisMessageListenerContainer.removeMessageListener(this);
        } catch (Exception ignored) {
            // ignore
        }
    }

    /**
     * 把事件广播到其它实例。
     * 载荷含 instanceId 与事件字段；失败只打日志，不影响本机写库。
     */
    public void broadcast(FlowExternalChangeEvent event) {
        if (event == null) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(event.toPayloadMap(false));
            payload.put("instanceId", instanceId);
            stringRedisTemplate.convertAndSend(CHANNEL, payload.toJSONString());
        } catch (Exception e) {
            log.debug("外部变更 Redis 广播失败: {}", e.toString());
        }
    }

    /**
     * 收到其它实例广播后，还原事件并推给本机已打开的画布订阅者。
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            JSONObject payload = JSON.parseObject(body);
            if (payload == null) {
                return;
            }
            if (instanceId.equals(payload.getString("instanceId"))) {
                return;
            }
            FlowExternalChangeEvent event = FlowExternalChangeEvent.builder()
                    .type(payload.getString("type"))
                    .testFlowId(payload.getLong("testFlowId"))
                    .testProjectId(payload.getLong("testProjectId"))
                    .source(payload.getString("source"))
                    .updateTime(payload.getString("updateTime"))
                    .graphRevision(payload.getLong("graphRevision"))
                    .runId(payload.getLong("runId"))
                    .keys(listOrEmpty(payload, "keys", String.class))
                    .changedNodeIds(listOrEmpty(payload, "changedNodeIds", String.class))
                    .nodePatches(listOrEmpty(payload, "nodePatches", Object.class))
                    .edgePatches(listOrEmpty(payload, "edgePatches", Object.class))
                    .deletedNodeIds(listOrEmpty(payload, "deletedNodeIds", String.class))
                    .deletedEdgeIds(listOrEmpty(payload, "deletedEdgeIds", String.class))
                    .build();
            hub.publish(event);
        } catch (Exception e) {
            log.debug("外部变更 Redis 消息处理失败: {}", e.toString());
        }
    }

    /** 取列表字段，缺省为空列表 */
    private static <T> List<T> listOrEmpty(JSONObject payload, String key, Class<T> type) {
        List<T> list = payload.getList(key, type);
        return list != null ? list : List.of();
    }
}
