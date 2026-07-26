package com.qualitest.flow.migrate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 图升级检测结果：供 GET 详情与升级确认弹窗使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphUpgradePreview {

    @Builder.Default
    private boolean upgradeAvailable = false;

    private int upgradeFromVersion;

    private int upgradeToVersion;

    @Builder.Default
    private List<String> upgradeSummary = new ArrayList<>();
}
