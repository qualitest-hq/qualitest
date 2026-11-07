package com.qualitest.project.runhub;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.qualitest.common.core.redis.RedisCache;
import com.qualitest.project.enums.RunConcurrencyMode;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.ProjectRunHubItemResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 项目跑流并发控制与运行中心活动索引（全部走 Redis，不落业务库）。
 * <ul>
 *   <li>按环境串行：每个测试环境一把锁；抢不到则入队，槽释放后再开跑</li>
 *   <li>活动索引：项目维度的「排队中 / 执行中」列表，供运行中心展示与取消</li>
 *   <li>排队绑定：queueItemId → runId（或 cancelled），供调用方轮询等到开跑</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EnvRunConcurrencyService {

    private static final Logger log = LoggerFactory.getLogger(EnvRunConcurrencyService.class);

    /** 环境跑流占用锁：key 后缀为环境 id，值为当前 runId */
    private static final String LOCK_KEY = "qualitest:env-run:lock:";
    /** 环境排队列表：key 后缀为环境 id，值为排队项 JSON */
    private static final String QUEUE_KEY = "qualitest:env-run:queue:";
    /** 项目运行中心活动 Hash：key 后缀为项目 id，field 为 itemId */
    private static final String HUB_KEY = "qualitest:run-hub:";
    /** 排队项开跑结果：key 后缀为 queueItemId，值为 runId 或 cancelled */
    private static final String QID_RUN_KEY = "qualitest:env-run:qid:";
    /** 锁与排队 TTL：进程异常退出后自动腾坑，避免永久占槽 */
    private static final int LOCK_TTL_HOURS = 6;
    /** 活动索引 TTL */
    private static final int HUB_TTL_HOURS = 24;

    private final RedisCache redisCache;

    /**
     * 判断项目是否启用「按环境串行」。
     * 空或未知模式按串行处理。
     */
    public boolean isSerialByEnv(String modeCode) {
        return RunConcurrencyMode.fromCode(modeCode) == RunConcurrencyMode.SERIAL_BY_ENV;
    }

    /**
     * 尝试占用环境跑流槽。
     * 成功写入「环境 → runId」锁并返回 true；
     * 若锁已是同一 runId（重复抢）也视为成功；否则返回 false（调用方应入队）。
     */
    public boolean tryAcquireEnvSlot(Long envId, Long runId) {
        if (envId == null || runId == null) {
            return true;
        }
        String key = LOCK_KEY + envId;
        boolean ok = redisCache.setIfAbsent(key, String.valueOf(runId), LOCK_TTL_HOURS, TimeUnit.HOURS);
        if (ok) {
            return true;
        }
        Object held = redisCache.getCacheObject(key);
        return runId.toString().equals(String.valueOf(held));
    }

    /**
     * 释放环境跑流槽。
     * 仅当当前锁持有者是该 runId（或 runId 为空）时才删除，避免误删别人的占用。
     */
    public void releaseEnvSlot(Long envId, Long runId) {
        if (envId == null) {
            return;
        }
        String key = LOCK_KEY + envId;
        Object held = redisCache.getCacheObject(key);
        if (held == null) {
            return;
        }
        if (runId != null && !runId.toString().equals(String.valueOf(held))) {
            return;
        }
        redisCache.deleteObject(key);
    }

    /**
     * 将触发请求入队，等待环境空闲。
     * 同时写入运行中心活动项（status=queued）。
     *
     * @return 排队项 id，调用方可据此轮询是否已开跑或取消排队
     */
    public String enqueue(EnvRunQueueItem item) {
        if (item == null || item.getTestProjectEnvId() == null) {
            throw new IllegalArgumentException("排队项缺少环境");
        }
        if (StrUtil.isBlank(item.getQueueItemId())) {
            item.setQueueItemId("q" + IdUtil.getSnowflakeNextId());
        }
        if (item.getEnqueuedAtMs() <= 0) {
            item.setEnqueuedAtMs(System.currentTimeMillis());
        }
        String qKey = QUEUE_KEY + item.getTestProjectEnvId();
        redisCache.redisTemplate.opsForList().rightPush(qKey, JSON.toJSONString(item));
        redisCache.expire(qKey, LOCK_TTL_HOURS, TimeUnit.HOURS);

        upsertHubActive(ProjectRunHubActiveItem.builder()
                .itemId("queue:" + item.getQueueItemId())
                .status("queued")
                .kind("queue")
                .source(resolveSource(item.getTriggerType()))
                .testProjectId(item.getTestProjectId())
                .testProjectEnvId(item.getTestProjectEnvId())
                .envName(item.getEnvName())
                .testFlowId(item.getTestFlowId())
                .flowName(item.getFlowName())
                .runScenarioId(item.getRunScenarioId())
                .triggerType(item.getTriggerType())
                .operatorUserId(item.getOperatorUserId())
                .updatedAtMs(item.getEnqueuedAtMs())
                .title(flowTitle(item.getFlowName(), item.getTestFlowId()))
                .build());
        return item.getQueueItemId();
    }

    /**
     * 弹出该环境排队队首。
     * 成功弹出时同步从运行中心活动列表移除对应 queue 行；队列空返回 null。
     */
    public EnvRunQueueItem pollNext(Long envId) {
        if (envId == null) {
            return null;
        }
        Object raw = redisCache.redisTemplate.opsForList().leftPop(QUEUE_KEY + envId);
        if (raw == null) {
            return null;
        }
        try {
            EnvRunQueueItem item = JSON.parseObject(String.valueOf(raw), EnvRunQueueItem.class);
            if (item != null && StrUtil.isNotBlank(item.getQueueItemId())) {
                removeHubActive(item.getTestProjectId(), "queue:" + item.getQueueItemId());
            }
            return item;
        } catch (Exception e) {
            log.warn("解析环境跑流排队项失败 envId={}: {}", envId, e.getMessage());
            return null;
        }
    }

    /**
     * 取消指定排队项：从环境队列移除，并清掉运行中心活动行。
     *
     * @return 被取消的排队项；找不到返回 null
     */
    public EnvRunQueueItem cancelQueued(Long projectId, Long envId, String queueItemId) {
        if (envId == null || StrUtil.isBlank(queueItemId)) {
            return null;
        }
        String qKey = QUEUE_KEY + envId;
        List<Object> all = redisCache.redisTemplate.opsForList().range(qKey, 0, -1);
        if (all == null || all.isEmpty()) {
            return null;
        }
        EnvRunQueueItem removed = null;
        List<String> kept = new ArrayList<>();
        for (Object raw : all) {
            if (raw == null) {
                continue;
            }
            try {
                EnvRunQueueItem item = JSON.parseObject(String.valueOf(raw), EnvRunQueueItem.class);
                if (item != null && queueItemId.equals(item.getQueueItemId())) {
                    removed = item;
                    continue;
                }
            } catch (Exception ignored) {
                // 解析失败的脏数据仍保留在队中，避免整队丢失
            }
            kept.add(String.valueOf(raw));
        }
        redisCache.deleteObject(qKey);
        if (!kept.isEmpty()) {
            for (String s : kept) {
                redisCache.redisTemplate.opsForList().rightPush(qKey, s);
            }
            redisCache.expire(qKey, LOCK_TTL_HOURS, TimeUnit.HOURS);
        }
        if (removed != null) {
            Long pid = projectId != null ? projectId : removed.getTestProjectId();
            removeHubActive(pid, "queue:" + queueItemId);
        }
        return removed;
    }

    /**
     * Run 已写入 running 并开始后台执行时调用：写入运行中心「执行中」活动项，并续期环境锁 TTL。
     */
    public void markRunStarted(
            Long projectId,
            Long envId,
            String envName,
            Long flowId,
            String flowName,
            Long runId,
            String scenarioId,
            String triggerType,
            Long operatorUserId) {
        if (projectId == null || runId == null) {
            return;
        }
        upsertHubActive(ProjectRunHubActiveItem.builder()
                .itemId("run:" + runId)
                .status("running")
                .kind("flow")
                .source(resolveSource(triggerType))
                .testProjectId(projectId)
                .testProjectEnvId(envId)
                .envName(envName)
                .testFlowId(flowId)
                .flowName(flowName)
                .testFlowRunId(runId)
                .runScenarioId(scenarioId)
                .triggerType(triggerType)
                .operatorUserId(operatorUserId)
                .updatedAtMs(System.currentTimeMillis())
                .title(flowTitle(flowName, flowId))
                .build());
        if (envId != null) {
            redisCache.expire(LOCK_KEY + envId, LOCK_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    /** Run 终态后从活动列表移除对应 run 行（正式报告仍在库表） */
    public void markRunFinished(Long projectId, Long runId) {
        removeHubActive(projectId, "run:" + runId);
    }

    /** 用户从运行中心活动区手动移除一行（不删库表正式报告） */
    public void dismissActive(Long projectId, String itemId) {
        removeHubActive(projectId, itemId);
    }

    /**
     * 列出项目当前活动项（排队 + 执行中），按更新时间倒序。
     */
    public List<ProjectRunHubItemResult> listActive(Long projectId) {
        List<ProjectRunHubItemResult> out = new ArrayList<>();
        if (projectId == null) {
            return out;
        }
        Map<String, Object> map = redisCache.getCacheMap(HUB_KEY + projectId);
        if (map == null || map.isEmpty()) {
            return out;
        }
        for (Object raw : map.values()) {
            if (raw == null) {
                continue;
            }
            try {
                ProjectRunHubActiveItem item = raw instanceof ProjectRunHubActiveItem active
                        ? active
                        : JSON.parseObject(String.valueOf(raw), ProjectRunHubActiveItem.class);
                if (item == null) {
                    continue;
                }
                out.add(toHubResult(item));
            } catch (Exception e) {
                log.debug("跳过损坏的运行中心活动项: {}", e.getMessage());
            }
        }
        out.sort(Comparator.comparing(
                (ProjectRunHubItemResult r) -> r.getStartedAt() == null ? 0L : r.getStartedAt().getTime()
        ).reversed());
        return out;
    }

    /**
     * 复制触发参数快照入队用。
     * 不复制 bypassEnvQueue，避免把「提升开跑」标记带到普通排队项。
     */
    public TriggerTestFlowRunParams snapshotParams(TriggerTestFlowRunParams params) {
        if (params == null) {
            return null;
        }
        return TriggerTestFlowRunParams.builder()
                .testFlowId(params.getTestFlowId())
                .testProjectEnvId(params.getTestProjectEnvId())
                .runScenarioId(params.getRunScenarioId())
                .triggerType(params.getTriggerType())
                .operatorUserId(params.getOperatorUserId())
                .build();
    }

    /**
     * 排队项真正开跑后，把 runId 绑到 queueItemId。
     * 前端与工具通过轮询此绑定拿到 runId，再跟随正式 Run。
     */
    public void bindQueueItemRunId(String queueItemId, Long runId) {
        if (StrUtil.isBlank(queueItemId) || runId == null) {
            return;
        }
        redisCache.setCacheObject(QID_RUN_KEY + queueItemId, String.valueOf(runId), LOCK_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 读取排队项已绑定的 runId。
     * 尚未开跑或已取消时返回 null。
     */
    public Long findRunIdForQueueItem(String queueItemId) {
        if (StrUtil.isBlank(queueItemId)) {
            return null;
        }
        Object raw = redisCache.getCacheObject(QID_RUN_KEY + queueItemId);
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        if ("cancelled".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** 排队项是否已被取消（绑定值为 cancelled） */
    public boolean isQueueItemCancelled(String queueItemId) {
        if (StrUtil.isBlank(queueItemId)) {
            return false;
        }
        Object raw = redisCache.getCacheObject(QID_RUN_KEY + queueItemId);
        return raw != null && "cancelled".equalsIgnoreCase(String.valueOf(raw));
    }

    /**
     * 标记排队项已取消。
     * 等待方读到 cancelled 后应停止轮询，避免无限等待。
     */
    public void bindQueueItemCancelled(String queueItemId) {
        if (StrUtil.isBlank(queueItemId)) {
            return;
        }
        redisCache.setCacheObject(QID_RUN_KEY + queueItemId, "cancelled", 1, TimeUnit.HOURS);
    }

    /** 写入或覆盖运行中心活动 Hash 中的一行，并续期桶 TTL */
    private void upsertHubActive(ProjectRunHubActiveItem item) {
        if (item == null || item.getTestProjectId() == null || StrUtil.isBlank(item.getItemId())) {
            return;
        }
        String key = HUB_KEY + item.getTestProjectId();
        redisCache.setCacheMapValue(key, item.getItemId(), item);
        redisCache.expire(key, HUB_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 删除运行中心活动 Hash 中的一行 */
    private void removeHubActive(Long projectId, String itemId) {
        if (projectId == null || StrUtil.isBlank(itemId)) {
            return;
        }
        redisCache.deleteCacheMapValue(HUB_KEY + projectId, itemId);
    }

    /** 缓存活动项转成接口返回结构 */
    private static ProjectRunHubItemResult toHubResult(ProjectRunHubActiveItem item) {
        Date at = item.getUpdatedAtMs() > 0 ? new Date(item.getUpdatedAtMs()) : null;
        return ProjectRunHubItemResult.builder()
                .itemId(item.getItemId())
                .status(item.getStatus())
                .source(item.getSource())
                .kind(item.getKind())
                .testProjectId(item.getTestProjectId())
                .testProjectEnvId(item.getTestProjectEnvId())
                .envName(item.getEnvName())
                .testFlowId(item.getTestFlowId())
                .flowName(item.getFlowName())
                .testFlowRunId(item.getTestFlowRunId())
                .runScenarioId(item.getRunScenarioId())
                .triggerType(item.getTriggerType())
                .operatorUserId(item.getOperatorUserId())
                .startedAt(at)
                .durationMs(null)
                .title(item.getTitle())
                .build();
    }

    /** 由 triggerType 归类运行中心来源标签 */
    private static String resolveSource(String triggerType) {
        String t = StrUtil.blankToDefault(triggerType, "manual").toLowerCase();
        if (t.contains("suite")) {
            return "suite";
        }
        if (t.contains("mcp")) {
            return "mcp";
        }
        if ("ai".equals(t)) {
            return "ai";
        }
        return t;
    }

    /** 活动行标题：流名优先，缺省时用流 id */
    private static String flowTitle(String flowName, Long flowId) {
        return StrUtil.blankToDefault(flowName, "流#" + flowId);
    }
}
