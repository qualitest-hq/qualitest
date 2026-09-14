package com.qualitest.ai.tools;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮 AI 设计中，项目鉴权 Profile 写入提案的内存容器。
 * <p>
 * 半自动模式下只累积待确认（pending）提案；全自动模式下工具已写库后记为已确认（confirmed）。
 * 同一 profileId 后写入覆盖先写入。
 */
@Getter
public class AuthProfileUpsertCapture {

    /** profileId → 最新提案；新建且尚无 id 时用临时键 _new_N */
    private final Map<String, AuthProfileUpsertProposal> byId = new LinkedHashMap<>();

    /**
     * 记录或覆盖一条提案。
     * profileId 为空时分配临时键，仅用于本轮内存去重。
     *
     * @param proposal 提案；null 忽略
     */
    public void record(AuthProfileUpsertProposal proposal) {
        if (proposal == null) {
            return;
        }
        String id = proposal.getProfileId();
        if (id == null || id.isBlank()) {
            id = "_new_" + byId.size();
        }
        byId.put(id.trim(), proposal);
    }

    /** 是否已有任意提案（含已确认 / 已拒绝） */
    public boolean hasProposals() {
        return !byId.isEmpty();
    }

    /** 是否仍有状态为空或 pending 的未确认提案 */
    public boolean hasPendingProposals() {
        for (AuthProfileUpsertProposal p : byId.values()) {
            if (p != null && (p.getStatus() == null
                    || AuthProfileUpsertProposal.STATUS_PENDING.equals(p.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    /** 返回当前全部提案副本（顺序与写入顺序一致） */
    public List<AuthProfileUpsertProposal> getProposals() {
        return new ArrayList<>(byId.values());
    }
}
