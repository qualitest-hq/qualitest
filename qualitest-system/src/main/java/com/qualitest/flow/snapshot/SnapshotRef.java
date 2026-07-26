package com.qualitest.flow.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 被测系统返回的快照创建结果。
 * <p>
 * snapshotId 由质衡写入 Run 快照栈，失败还原时传给 restore 接口；被测库本身可不存元数据表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotRef {

    /** 快照唯一标识，后续 restore 使用 */
    private String snapshotId;

    /** 快照创建时间，ISO-8601 字符串 */
    private String createdAt;

    /** 实际备份范围类型 */
    private String scope;

    /** 快照状态，例如 ready */
    private String status;
}
