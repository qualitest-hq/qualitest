package com.qualitest.ai.tools;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮 AI 设计里素材库写入的内存容器。
 * <p>
 * 半自动：upsert 只记提案（status=pending），不写数据库；Agent 结束后提案进入助手消息元数据，
 * 用户在聊天侧确认后再真正 insert/update。
 * 全自动：upsert 工具内已写库，此处记 status=confirmed，供回执与前端展示「已写入」。
 * 同一 key 多次调用时后一次覆盖前一次。每轮设计请求新建实例。
 */
@Getter
public class AssetUpsertCapture {

    /** 按 key 存放提案，LinkedHashMap 保留首次出现顺序 */
    private final Map<String, AssetUpsertProposal> byKey = new LinkedHashMap<>();

    /**
     * 记入一条提案。
     * key 为空则忽略；同 key 已存在则覆盖。
     */
    public void record(AssetUpsertProposal proposal) {
        if (proposal == null || proposal.getKey() == null || proposal.getKey().isBlank()) {
            return;
        }
        byKey.put(proposal.getKey().trim(), proposal);
    }

    /** 本轮是否至少有一条提案 */
    public boolean hasProposals() {
        return !byKey.isEmpty();
    }

    /** 本轮是否仍有待用户确认的提案（全自动落盘后为 confirmed，不算 pending） */
    public boolean hasPendingProposals() {
        for (AssetUpsertProposal p : byKey.values()) {
            if (p != null && (p.getStatus() == null
                    || AssetUpsertProposal.STATUS_PENDING.equals(p.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按记录顺序返回提案列表的副本，供组装响应与落库元数据。
     */
    public List<AssetUpsertProposal> getProposals() {
        return new ArrayList<>(byKey.values());
    }
}
