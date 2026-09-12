package com.qualitest.ai.tools;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮 AI 设计里素材库写入提案的内存容器。
 * <p>
 * upsert 工具只把提案记入本容器，不写数据库；
 * 本轮 Agent 结束后，编排层把提案列表写入接口响应和助手消息元数据。
 * 同一 key 多次调用时，后一次覆盖前一次。每轮设计请求新建一个实例，用完即弃。
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
