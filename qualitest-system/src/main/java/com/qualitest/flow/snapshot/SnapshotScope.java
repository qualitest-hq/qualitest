package com.qualitest.flow.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 checkpoint 要备份的数据范围。
 * <p>
 * 来自节点 data.snapshotScope；未配置时默认 scope=tables、tables 为空（由被测方决定 fallback 行为）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotScope {

    /** 表级备份，当前唯一使用的 scope 取值 */
    public static final String SCOPE_TABLES = "tables";

    /** 范围类型，例如 tables */
    private String scope;

    /** scope=tables 时要备份的表名列表；建议只列本节点会写的表，避免全库 dump */
    @Builder.Default
    private List<String> tables = new ArrayList<>();
}
