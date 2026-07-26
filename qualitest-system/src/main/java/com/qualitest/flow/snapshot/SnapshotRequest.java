package com.qualitest.flow.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质衡发起的一次快照创建请求参数。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotRequest {

    /** 被测快照服务根地址，POST 时拼 /snapshot */
    private String resetEndpointBase;

    /** 备份范围类型 */
    private String scope;

    /** 要备份的表名 */
    private List<String> tables;

    /**
     * 关联标签，格式为 runId:nodeId，便于日志与被测方定位是哪次 Run、哪个节点打的 checkpoint
     */
    private String label;

    /** 附加信息，如环境名、节点展示名 */
    @Builder.Default
    private Map<String, Object> meta = new HashMap<>();

    /** HTTP 调用超时（毫秒） */
    private long timeoutMs;
}
