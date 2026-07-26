package com.qualitest.dashboard.result;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 近7天运行状态计数（用于计算通过率）
 */
@Getter
@Setter
@ToString
@Alias("DashboardRunStatusCount")
public class DashboardRunStatusCount implements Serializable {

    private Long passedCount;

    private Long finishedCount;
}
