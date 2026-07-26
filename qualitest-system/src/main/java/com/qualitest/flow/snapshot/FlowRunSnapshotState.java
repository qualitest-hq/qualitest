package com.qualitest.flow.snapshot;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 单次 Run 执行期间的被测数据 checkpoint 栈（内存态）。
 * <p>
 * 按节点执行顺序压入 nodeId + snapshotId；Run 暂停时序列化进库，续跑时恢复。
 * 与流程图 JSON 快照、Run 头字段无关，仅表示本 Run 已打过的数据还原点。
 */
public class FlowRunSnapshotState {

    private final List<SnapshotStackEntry> stack = new ArrayList<>();

    public FlowRunSnapshotState() {
    }

    public static FlowRunSnapshotState fromEntries(List<SnapshotStackEntry> entries) {
        FlowRunSnapshotState state = new FlowRunSnapshotState();
        if (entries != null) {
            state.stack.addAll(entries);
        }
        return state;
    }

    public static FlowRunSnapshotState fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new FlowRunSnapshotState();
        }
        List<SnapshotStackEntry> entries = JSON.parseArray(json, SnapshotStackEntry.class);
        return fromEntries(entries);
    }

    /** checkpoint 成功后追加一条记录 */
    public void push(String nodeId, String snapshotId) {
        stack.add(new SnapshotStackEntry(nodeId, snapshotId));
    }

    /** 只读栈副本，按时间从早到晚 */
    public List<SnapshotStackEntry> entries() {
        return Collections.unmodifiableList(stack);
    }

    /** 最近一次 checkpoint；栈空时 null */
    public SnapshotStackEntry peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.get(stack.size() - 1);
    }

    /** 按 snapshotId 查找栈项 */
    public Optional<SnapshotStackEntry> findBySnapshotId(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        for (SnapshotStackEntry entry : stack) {
            if (snapshotId.equals(entry.getSnapshotId())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * 还原到指定 snapshot 后，丢弃该 snapshot 之后的栈项（保留该 snapshot 及之前记录）。
     *
     * @return 是否找到并截断
     */
    public boolean truncateAfter(String snapshotId) {
        for (int i = 0; i < stack.size(); i++) {
            if (snapshotId.equals(stack.get(i).getSnapshotId())) {
                if (i < stack.size() - 1) {
                    stack.subList(i + 1, stack.size()).clear();
                }
                return true;
            }
        }
        return false;
    }

    public String toJson() {
        return JSON.toJSONString(stack);
    }
}
