package com.qualitest.dashboard.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 首页运行趋势（按日）
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("DashboardRunTrendItem")
public class DashboardRunTrendItem implements Serializable {

    private String date;

    private Long passed;

    private Long failed;

    private Long cancelled;

    private Long total;
}
